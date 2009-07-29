/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.compiler;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.ModuleCompilerUtil;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.impl.javaCompiler.ModuleChunk;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.compiler.TranslatingCompiler;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Chunk;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.groovy.compiler.rt.CompilerMessage;
import org.jetbrains.groovy.compiler.rt.GroovycRunner;
import org.jetbrains.groovy.compiler.rt.MessageCollector;
import org.jetbrains.plugins.grails.config.GrailsModuleStructureUtil;
import org.jetbrains.plugins.grails.util.GrailsUtils;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.config.GroovyFacet;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

/**
 * @author peter
 */
public abstract class GroovyCompilerBase implements TranslatingCompiler {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.compiler.GroovyCompilerBase");
  private static final HashSet<String> required = new HashSet<String>(Arrays.asList("groovy", "asm", "antlr", "junit", "jline", "ant", "commons"));
  protected final Project myProject;

  public GroovyCompilerBase(Project project) {
    myProject = project;
  }

  protected void runGroovycCompiler(CompileContext compileContext, Set<OutputItem> successfullyCompiled, Set<VirtualFile> toRecompile, final Module module,
                         final List<VirtualFile> toCompile, boolean forStubs) {
    GeneralCommandLine commandLine = new GeneralCommandLine();
    final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    assert sdk != null; //verified before
    SdkType sdkType = sdk.getSdkType();
    assert sdkType instanceof JavaSdkType;
    commandLine.setExePath(((JavaSdkType)sdkType).getVMExecutablePath(sdk));

    String rtJarPath = PathUtil.getJarPathForClass(GroovycRunner.class);
    final StringBuilder classPathBuilder = new StringBuilder();
    classPathBuilder.append(rtJarPath);
    classPathBuilder.append(File.pathSeparator);

    final String libPath = FileUtil.toSystemIndependentName(LibrariesUtil.getGroovyHomePath(module) + "/lib");
    VirtualFile lib = LocalFileSystem.getInstance().findFileByPath(libPath);
    if (lib != null) {
      for (VirtualFile file : lib.getChildren()) {
        if (required(file.getName())) {
          classPathBuilder.append(file.getPath());
          classPathBuilder.append(File.pathSeparator);
        }
      }
    }

    classPathBuilder.append(getModuleSpecificClassPath(module));
    if ("true".equals(System.getProperty("profile.groovy.compiler"))) {
      commandLine.addParameter("-Djava.library.path=" + PathManager.getBinPath());
      commandLine.addParameter("-Dprofile.groovy.compiler=true");
      commandLine.addParameter("-agentlib:yjpagent=disablej2ee,disablecounts,disablealloc,sessionname=GroovyCompiler");
      classPathBuilder.append(PathManager.getLibPath()).append("/yjp-controller-api-redist.jar").append(File.pathSeparator);
    }

    commandLine.addParameter("-cp");
    commandLine.addParameter(classPathBuilder.toString());

    commandLine.addParameter("-Xmx" + System.getProperty("groovy.compiler.Xmx", "400m"));
    commandLine.addParameter("-XX:+HeapDumpOnOutOfMemoryError");

    //debug
    //commandLine.addParameter("-Xdebug"); commandLine.addParameter("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5239");

    // Setting up process encoding according to locale
    final ArrayList<String> list = new ArrayList<String>();
    CompilerUtil.addLocaleOptions(list, false);
    commandLine.addParameters(list);

    commandLine.addParameter(GroovycRunner.class.getName());

    try {
      File fileWithParameters = File.createTempFile("toCompile", "");
      fillFileWithGroovycParameters(module, toCompile, fileWithParameters, forStubs, compileContext);

      commandLine.addParameter(fileWithParameters.getPath());
    }
    catch (IOException e) {
      LOG.error(e);
    }

    GroovycOSProcessHandler processHandler;

    try {
      processHandler = new GroovycOSProcessHandler(compileContext, commandLine.createProcess(), commandLine.getCommandLineString());

      processHandler.startNotify();
      processHandler.waitFor();

      Set<File> toRecompileFiles = processHandler.getToRecompileFiles();
      for (File toRecompileFile : toRecompileFiles) {
        final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(toRecompileFile);
        LOG.assertTrue(vFile != null);
        toRecompile.add(vFile);
      }

      for (CompilerMessage compilerMessage : processHandler.getCompilerMessages()) {
        final CompilerMessageCategory category;
        category = getMessageCategory(compilerMessage);

        final String url = compilerMessage.getUrl();

        compileContext.addMessage(category, compilerMessage.getMessage(), VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(url)), compilerMessage.getLineNum(),
                                  compilerMessage.getColumnNum());
      }

      StringBuffer unparsedBuffer = processHandler.getUnparsedOutput();
      if (unparsedBuffer.length() != 0) compileContext.addMessage(CompilerMessageCategory.ERROR, unparsedBuffer.toString(), null, -1, -1);

      successfullyCompiled.addAll(processHandler.getSuccessfullyCompiled());
    }
    catch (ExecutionException e) {
      LOG.error(e);
    }
  }

  private static String getModuleSpecificClassPath(final Module module) {
    final StringBuffer buffer = new StringBuffer();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        ModuleRootManager manager = ModuleRootManager.getInstance(module);
        for (OrderEntry entry : manager.getOrderEntries()) {
          if (entry instanceof LibraryOrderEntry) {
            Library library = ((LibraryOrderEntry)entry).getLibrary();
            if (library == null) continue;
            for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
              String path = file.getPath();
              if (path != null && path.endsWith(".jar!/")) {
                buffer.append(StringUtil.trimEnd(path, "!/")).append(File.pathSeparator);
              }
            }
          }
        }
      }
    });
    return buffer.toString();
  }

  private static boolean required(String name) {
    name = name.toLowerCase();
    if (!name.endsWith(".jar")) return false;

    name = name.substring(0, name.lastIndexOf('.'));
    int ind = name.lastIndexOf('-');
    if (ind != -1 && name.length() > ind + 1 && Character.isDigit(name.charAt(ind + 1))) {
      name = name.substring(0, ind);
    }

    for (String requiredStr : required) {
      if (name.contains(requiredStr)) return true;
    }

    return false;
  }

  private static CompilerMessageCategory getMessageCategory(CompilerMessage compilerMessage) {
    String category;
    category = compilerMessage.getCategory();

    if (MessageCollector.ERROR.equals(category)) return CompilerMessageCategory.ERROR;
    if (MessageCollector.INFORMATION.equals(category)) return CompilerMessageCategory.INFORMATION;
    if (MessageCollector.STATISTICS.equals(category)) return CompilerMessageCategory.STATISTICS;
    if (MessageCollector.WARNING.equals(category)) return CompilerMessageCategory.WARNING;

    return CompilerMessageCategory.ERROR;
  }

  private void fillFileWithGroovycParameters(Module module, List<VirtualFile> virtualFiles, File f, boolean forStubs, CompileContext context) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Running groovyc on: " + virtualFiles.toString());
    }

    FileOutputStream stream;
    try {
      stream = new FileOutputStream(f);
    }
    catch (FileNotFoundException e) {
      LOG.error(e);
      return;
    }

    final PrintStream printer = new PrintStream(stream);

/*    filename1
*    filname2
*    filname3
*    ...
*    filenameN
*/

    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    //files
    for (final VirtualFile item : virtualFiles) {
      final boolean isSource = !moduleRootManager.getFileIndex().isInTestSourceContent(item);
      if (isSource) {
        printer.println(GroovycRunner.SRC_FILE);
      }
      else {
        printer.println(GroovycRunner.TEST_FILE);
      }
      printer.println(item.getPath());
      if (isSource) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            final PsiFile file = PsiManager.getInstance(myProject).findFile(item);
            if (file instanceof GroovyFileBase) {
              for (PsiClass psiClass : ((GroovyFileBase)file).getClasses()) {
                printer.println(psiClass.getQualifiedName());
              }
            }
          }
        });
        printer.println(GroovycRunner.END);
      }
    }

    //classpath
    printer.println(GroovycRunner.CLASSPATH);
    final ModuleChunk chunk =
      new ModuleChunk((CompileContextEx)context, new Chunk<Module>(module), Collections.<Module, List<VirtualFile>>emptyMap());
    printer.println(chunk.getCompilationClasspath() + File.pathSeparator + CompilerPaths.getModuleOutputPath(module, false) + File.pathSeparator + CompilerPaths.getModuleOutputPath(module, true));

    //Grails injections  support
    printer.println(GroovycRunner.IS_GRAILS);
    printer.println(GrailsUtils.hasGrailsSupport(module) || GrailsModuleStructureUtil.isCommonPluginsModule(module) || GrailsModuleStructureUtil.isCustomPluginModule(module));

    printer.println(GroovycRunner.FOR_STUBS);
    printer.println(forStubs);

    final Charset ideCharset = EncodingProjectManager.getInstance(myProject).getDefaultCharset();
    if (!Comparing.equal(CharsetToolkit.getDefaultSystemCharset(), ideCharset)) {
      printer.println(GroovycRunner.ENCODING);
      printer.println(ideCharset.name());
    }

    //production output
    printer.println(GroovycRunner.OUTPUTPATH);
    printer.println(PathUtil.getLocalPath(context.getModuleOutputDirectory(module)));

    //test output
    printer.println(GroovycRunner.TEST_OUTPUTPATH);
    printer.println(PathUtil.getLocalPath(context.getModuleOutputDirectoryForTests(module)));
    printer.close();
  }

  @Nullable
  public ExitStatus compile(final CompileContext compileContext, final VirtualFile[] virtualFiles) {

    Set<OutputItem> successfullyCompiled = new HashSet<OutputItem>();
    Set<VirtualFile> toRecompileCollector = new HashSet<VirtualFile>();

    Map<Module, List<VirtualFile>> mapModulesToVirtualFiles = CompilerUtil.buildModuleToFilesMap(compileContext, virtualFiles);
    final List<Chunk<Module>> chunks =
      ModuleCompilerUtil.getSortedModuleChunks(myProject, new ArrayList<Module>(mapModulesToVirtualFiles.keySet()));
    for (final Chunk<Module> chunk : chunks) {
      for (final Module module : chunk.getNodes()) {
        final List<VirtualFile> moduleFiles = mapModulesToVirtualFiles.get(module);
        if (!toRecompileCollector.isEmpty()) {
          toRecompileCollector.addAll(moduleFiles);
          continue;
        }

        final GroovyFacet facet = GroovyFacet.getInstance(module);
        final List<VirtualFile> toCompile = new ArrayList<VirtualFile>();
        final List<VirtualFile> toCopy = new ArrayList<VirtualFile>();
        final CompilerConfiguration configuration = CompilerConfiguration.getInstance(myProject);
        if (module.getModuleType() instanceof JavaModuleType) {
          final boolean compileGroovyFiles = facet != null && facet.getConfiguration().isCompileGroovyFiles();
          for (final VirtualFile file : moduleFiles) {
            final boolean shouldCompile = module.getModuleType() instanceof JavaModuleType &&
                                          (file.getFileType() == GroovyFileType.GROOVY_FILE_TYPE && compileGroovyFiles ||
                                           file.getFileType() == StdFileTypes.JAVA);
            (shouldCompile ? toCompile : toCopy).add(file);
          }
        }

        if (!toCompile.isEmpty()) {
          compileFiles(compileContext, successfullyCompiled, toRecompileCollector, module, toCompile);
        }

        if (!toCopy.isEmpty()) {
          copyFiles(compileContext, successfullyCompiled, toRecompileCollector, toCopy, configuration);
        }

      }
    }


    final Set<OutputItem> compiledItems = successfullyCompiled;
    final VirtualFile[] toRecompile = toRecompileCollector.toArray(new VirtualFile[toRecompileCollector.size()]);
    return new ExitStatus() {
      private final OutputItem[] myCompiledItems = compiledItems.toArray(new OutputItem[compiledItems.size()]);
      private final VirtualFile[] myToRecompile = toRecompile;

      public OutputItem[] getSuccessfullyCompiled() {
        return myCompiledItems;
      }

      public VirtualFile[] getFilesToRecompile() {
        return myToRecompile;
      }
    };
  }

  protected abstract void copyFiles(CompileContext compileContext, Set<OutputItem> successfullyCompiled, Set<VirtualFile> toRecompileCollector, List<VirtualFile> toCopy,
                         CompilerConfiguration configuration);

  protected abstract void compileFiles(CompileContext compileContext, Set<OutputItem> successfullyCompiled, Set<VirtualFile> toRecompileCollector,
                            Module module, List<VirtualFile> toCompile);

  public boolean isCompilableFile(VirtualFile file, CompileContext context) {
    final boolean result = GroovyFileType.GROOVY_FILE_TYPE.equals(file.getFileType());
    if (result && LOG.isDebugEnabled()) {
      LOG.debug("compilable file: " + file.getPath());
    }
    return result;
  }
}
