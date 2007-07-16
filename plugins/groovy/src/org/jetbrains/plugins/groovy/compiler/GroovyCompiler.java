/*
 * Copyright 2000-2007 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.compiler;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.compiler.rt.GroovycRunner;
import org.jetbrains.plugins.groovy.config.GroovyGrailsConfiguration;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 16.04.2007
 */

public class GroovyCompiler implements TranslatingCompiler {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.compiler.GroovyCompiler");

  private static final String GROOVYC_RUNNER_QUALIFIED_NAME = "org.jetbrains.plugins.groovy.compiler.rt.GroovycRunner";
  private static final String JAVA_EXE = "java";
  private static final String CLASS_PATH_LIST_SEPARATOR = File.pathSeparator;
  private Project myProject;

  public GroovyCompiler(Project project) {
    myProject = project;
  }

  @Nullable
  public TranslatingCompiler.ExitStatus compile(final CompileContext compileContext, final VirtualFile[] virtualFiles) {
    Set<TranslatingCompiler.OutputItem> successfullyCompiled = new HashSet<TranslatingCompiler.OutputItem>();
    Set<VirtualFile> toRecompile = new HashSet<VirtualFile>();

    GeneralCommandLine commandLine;

    Map<Module, Set<VirtualFile>> mapModulesToVirtualFiles = buildModuleToFilesMap(compileContext, virtualFiles);

    for (Map.Entry<Module, Set<VirtualFile>> entry : mapModulesToVirtualFiles.entrySet()) {

      commandLine = new GeneralCommandLine();
      commandLine.setExePath(JAVA_EXE);

      commandLine.addParameter("-cp");

      String myJarPath = PathUtil.getJarPathForClass(getClass());
      final StringBuilder classPathBuilder = new StringBuilder();
      classPathBuilder.append(myJarPath);
      classPathBuilder.append(CLASS_PATH_LIST_SEPARATOR);
      
      String libPath = GroovyGrailsConfiguration.getInstance().getGroovyInstallPath() + "/lib";
      libPath = libPath.replace(File.separatorChar, '/');
      VirtualFile lib = LocalFileSystem.getInstance().findFileByPath(libPath);
      if (lib != null) {
        for (VirtualFile file : lib.getChildren()) {
          if (required(file.getName())) {
            classPathBuilder.append(file.getPath());
            classPathBuilder.append(CLASS_PATH_LIST_SEPARATOR);
          }
        }
      }

      commandLine.addParameter(classPathBuilder.toString());

      commandLine.addParameter(GROOVYC_RUNNER_QUALIFIED_NAME);

      try {
        File fileWithParameters = File.createTempFile("toCompile", "");
        fillFileWithGroovycParameters(entry.getKey(), entry.getValue(), fileWithParameters);

        commandLine.addParameter(fileWithParameters.getPath());
      } catch (IOException e) {
        e.printStackTrace();
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

          String url = compilerMessage.getUrl();


          final GroovyFile[] myPsiFile = new GroovyFile[1];
          final VirtualFile myFile;

          try {
            myFile = VfsUtil.findFileByURL(new URL("file://" + url));
            assert myFile != null;


            final Project project = VfsUtil.guessProjectForFile(myFile);
            assert project != null;

            ApplicationManager.getApplication().runReadAction(new Runnable() {
              public void run() {
                myPsiFile[0] = (GroovyFile) PsiManager.getInstance(project).findFile(myFile);
              }
            });

            assert myPsiFile[0] != null;
          } catch (MalformedURLException e) {
            e.printStackTrace();
          }

          url = url.replace('\\', '/');
          compileContext.addMessage(category, compilerMessage.getMessage(), url, compilerMessage.getLinenum(), compilerMessage.getColomnnum());
        }

        StringBuffer unparsedBuffer = processHandler.getUnparsedOutput();
        if (unparsedBuffer.length() != 0)
          compileContext.addMessage(CompilerMessageCategory.ERROR, unparsedBuffer.toString(), null, -1, -1);

        successfullyCompiled.addAll(processHandler.getSuccessfullyCompiled());
      } catch (ExecutionException e) {
        e.printStackTrace();
      }
    }
    return new GroovyCompileExitStatus(successfullyCompiled, toRecompile.toArray(new VirtualFile[toRecompile.size()]));
  }


  static HashSet<String> required = new HashSet<String>();

  static {
    required.add("groovy");
    required.add("asm");
    required.add("antlr");
    required.add("junit");
  }

  private boolean required(String name) {
    name = name.toLowerCase();
    if (!name.endsWith(".jar"))
      return false;

    name = name.substring(0, name.indexOf('.'));
    int ind = name.lastIndexOf('-');
    if (ind != -1 && name.length() > ind + 1 && Character.isDigit(name.charAt(ind + 1))) {
      name = name.substring(0, ind);
    }

    return required.contains(name);
  }

  private CompilerMessageCategory getMessageCategory(CompilerMessage compilerMessage) {
    String cathegory;
    cathegory = compilerMessage.getCathegory();

    if (MessageCollector.ERROR.equals(cathegory)) return CompilerMessageCategory.ERROR;
    if (MessageCollector.INFORMATION.equals(cathegory)) return CompilerMessageCategory.INFORMATION;
    if (MessageCollector.STATISTICS.equals(cathegory)) return CompilerMessageCategory.STATISTICS;
    if (MessageCollector.WARNING.equals(cathegory)) return CompilerMessageCategory.WARNING;

    return CompilerMessageCategory.ERROR;
  }

  class GroovyCompileExitStatus implements ExitStatus {
    private OutputItem[] myCompiledItems;
    private VirtualFile[] myToRecompile;

    public GroovyCompileExitStatus(Set<TranslatingCompiler.OutputItem> compiledItems, VirtualFile[] toRecompile) {
      myToRecompile = toRecompile;
      myCompiledItems = compiledItems.toArray(new OutputItem[compiledItems.size()]);
    }

    public OutputItem[] getSuccessfullyCompiled() {
      return myCompiledItems;
    }

    public VirtualFile[] getFilesToRecompile() {
      return myToRecompile;
    }
  }

  private void fillFileWithGroovycParameters(Module module, Set<VirtualFile> virtualFiles, File f) {

    PrintStream printer = null;
    try {
      printer = new PrintStream(new FileOutputStream(f));

/*    filename1
*    filname2
*    filname3
*    ...
*    filenameN
*/

      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      //files
      for (VirtualFile item : virtualFiles) {
        if (!moduleRootManager.getFileIndex().isInTestSourceContent(item)) {
          printer.println(GroovycRunner.SRC_FILE);
        } else {
          printer.println(GroovycRunner.TEST_FILE);
        }
        printer.println(item.getPath());
      }

      //classpath
      printer.println(GroovycRunner.CLASSPATH);
      printer.println(getCompilationClasspath(module).getPathsString());

//production output
      printer.println(GroovycRunner.OUTPUTPATH);
      printer.println(CompilerPaths.getModuleOutputPath(module, false));

//test output
      printer.println(GroovycRunner.TEST_OUTPUTPATH);
      printer.println(CompilerPaths.getModuleOutputPath(module, true));

//module name
      printer.println(GroovycRunner.MODULE_NAME);
      printer.println(module.getName());

//source
      printer.println(GroovycRunner.SRC_FOLDERS);
      printer.println(getNonExcludedModuleSourceFolders(module).getPathsString());

    } catch (IOException e) {
      e.printStackTrace();
    } finally {

      assert printer != null;
      printer.close();
    }
  }

  public boolean isCompilableFile(VirtualFile virtualFile, CompileContext compileContext) {
    return GroovyFileType.GROOVY_FILE_TYPE.equals(virtualFile.getFileType());
  }

  @NotNull
  public String getDescription() {
    return "groovy compiler";
  }

  public boolean validateConfiguration(CompileScope compileScope) {
    if (compileScope.getFiles(GroovyFileType.GROOVY_FILE_TYPE, true).length == 0) return true;

    final String groovyInstallPath = GroovyGrailsConfiguration.getInstance().getGroovyInstallPath();
    if (groovyInstallPath == null || groovyInstallPath.length() == 0) {
      Messages.showErrorDialog(myProject, GroovyBundle.message("cannot.compile.groovy.files"), GroovyBundle.message("cannot.compile"));
      return false;
    }

    return true;
  }

  private PathsList getCompilationClasspath(Module module) {
    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    OrderEntry[] entries = rootManager.getOrderEntries();
    Set<VirtualFile> cpVFiles = new HashSet<VirtualFile>();

    for (OrderEntry orderEntry : entries) {
      cpVFiles.addAll(Arrays.asList(orderEntry.getFiles(OrderRootType.COMPILATION_CLASSES)));
    }

    StringBuffer path = new StringBuffer();

    VirtualFile[] filesArray = cpVFiles.toArray(new VirtualFile[cpVFiles.size()]);
    for (int i = 0; i < filesArray.length; i++) {
      VirtualFile file = filesArray[i];
      String filePath = file.getPath();

      int jarSeparatorIndex = filePath.indexOf(JarFileSystem.JAR_SEPARATOR);
      if (jarSeparatorIndex > 0) {
        filePath = filePath.substring(0, jarSeparatorIndex);
      }
      path.append(filePath);

      if (i < filesArray.length - 1) {
        path.append(CLASS_PATH_LIST_SEPARATOR);
      }
    }

    PathsList pathsList = new PathsList();
    pathsList.add(path.toString());

    return pathsList;
  }

  public PathsList getNonExcludedModuleSourceFolders(Module module) {
    ContentEntry[] contentEntries = ModuleRootManager.getInstance(module).getContentEntries();
    PathsList sourceFolders = findAllSourceFolders(contentEntries);
    sourceFolders.getPathList().removeAll(findExcludedFolders(contentEntries));
    return sourceFolders;
  }

  private PathsList findAllSourceFolders(ContentEntry[] contentEntries) {
    PathsList sourceFolders = new PathsList();
    for (ContentEntry contentEntry : contentEntries) {
      for (SourceFolder folder : contentEntry.getSourceFolders()) {
        VirtualFile file = folder.getFile();
        if (file == null) continue;

        if (file.isDirectory() && file.isWritable()) {
          sourceFolders.add(file);
        }
      }
    }
    return sourceFolders;
  }

  private Set<VirtualFile> findExcludedFolders(ContentEntry[] entries) {
    Set<VirtualFile> excludedFolders = new HashSet<VirtualFile>();
    for (ContentEntry entry : entries) {
      excludedFolders.addAll(Arrays.asList(entry.getExcludeFolderFiles()));
    }
    return excludedFolders;
  }

  private static Map<Module, Set<VirtualFile>> buildModuleToFilesMap(final CompileContext context, final VirtualFile[] files) {
    final Map<Module, Set<VirtualFile>> map = new HashMap<Module, Set<VirtualFile>>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (VirtualFile file : files) {
          final Module module = context.getModuleByFile(file);

          if (module == null) {
            continue;
          }

          Set<VirtualFile> moduleFiles = map.get(module);
          if (moduleFiles == null) {
            moduleFiles = new HashSet<VirtualFile>();
            map.put(module, moduleFiles);
          }
          moduleFiles.add(file);
        }
      }
    });
    return map;
  }
}
