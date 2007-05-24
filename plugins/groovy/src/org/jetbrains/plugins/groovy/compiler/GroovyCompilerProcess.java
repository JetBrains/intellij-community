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
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

public class GroovyCompilerProcess implements TranslatingCompiler {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.compiler.GroovyCompilerProcess");

  private static final String GROOVYC_RUNNER_QUALIFIED_NAME = "org.jetbrains.plugins.groovy.compiler.rt.GroovycRunner";
  private static final String JAVA_EXE = "java";
  private static final String GROOVY_LANG_JAR = "embeddable/groovy-all-1.0.jar";

  private static final String GROOVY_LIB = "lib";
  private static final String CLASS_PATH_LIST_SEPARATOR = File.pathSeparator;
  private static final String antlrLibName = "antlr.jar";

  @Nullable
  public TranslatingCompiler.ExitStatus compile(final CompileContext compileContext, final VirtualFile[] virtualFiles) {
    Set<TranslatingCompiler.OutputItem> compiledItems = new HashSet<TranslatingCompiler.OutputItem>();
    Set<VirtualFile> allCompiling = new HashSet<VirtualFile>();

    GeneralCommandLine commandLine;

    Map<Module, Set<VirtualFile>> mapModulesToVirtualFiles = buildModuleToFilesMap(compileContext, virtualFiles);

    for (Map.Entry<Module, Set<VirtualFile>> entry : mapModulesToVirtualFiles.entrySet()) {

      commandLine = new GeneralCommandLine();
      commandLine.setExePath(JAVA_EXE);

      commandLine.addParameter("-cp");

//      String asmLibPath = PathManager.getLibPath() + File.separator + "asm.jar";
//
//      PluginId groovyPluginId = PluginManager.getPluginByClassName(getClass().getName());
//      IdeaPluginDescriptor ideaGroovyPluginDescriptor = PluginManager.getPlugin(groovyPluginId);
//
//      assert ideaGroovyPluginDescriptor != null;
//      String groovyPluginPath = ideaGroovyPluginDescriptor.getPath().getPath();
//      assert groovyPluginPath != null;

//      String antlrLib = groovyPluginPath + File.separator + GROOVY_LIB + File.separator + antlrLibName;
//      String groovyLangJarPath = GroovyGrailsConfiguration.getInstance().getGroovyInstallPath() + "\\" + GROOVY_LANG_JAR;

      String myJarPath = PathUtil.getJarPathForClass(getClass());
      final StringBuilder classPathBuilder = new StringBuilder();
      classPathBuilder.append(myJarPath);
      classPathBuilder.append(CLASS_PATH_LIST_SEPARATOR);
      
      String libPath = GroovyGrailsConfiguration.getInstance().getGroovyInstallPath() + "/lib";
      libPath = libPath.replace(File.separatorChar, '/');
      VirtualFile lib = LocalFileSystem.getInstance().findFileByPath(libPath);
      for (VirtualFile file : lib.getChildren()) {
        if (required(file.getName())) {
          classPathBuilder.append(file.getPath());
          classPathBuilder.append(CLASS_PATH_LIST_SEPARATOR);
        }
      }
//      classPathBuilder.append(myJarPath).
//          append(CLASS_PATH_LIST_SEPARATOR).
//          append(groovyLangJarPath).
//          append(CLASS_PATH_LIST_SEPARATOR).
//          append(antlrLib).
//          append(CLASS_PATH_LIST_SEPARATOR).
//          append(asmLibPath).
//          append(CLASS_PATH_LIST_SEPARATOR);

//      final Module key = entry.getKey();
//
//      ApplicationManager.getApplication().runReadAction(new Runnable()
//      {
//        public void run()
//        {
//          ModuleRootManager rootManager = ModuleRootManager.getInstance(key);
//          ModifiableRootModel model = rootManager.getModifiableModel();
//          VirtualFile[] files = model.getOrderedRoots(OrderRootType.CLASSES_AND_OUTPUT);
//
//          for (VirtualFile file : files)
//          {
//            if (file.getFileSystem() instanceof JarFileSystem)
//            {
//              JarFileSystem jarFileSystem = (JarFileSystem) file.getFileSystem();
//              classPathBuilder
//                      .append(jarFileSystem.getVirtualFileForJar(file).getPath())
//                      .append(CLASS_PATH_LIST_SEPARATOR);
//            }
//            else
//              classPathBuilder
//                      .append(file.getPath())
//                      .append(CLASS_PATH_LIST_SEPARATOR);
//          }
//        }
//      });

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
        VirtualFile[] toRecompileVirtualFiles = new VirtualFile[toRecompileFiles.size()];

        VirtualFile toRecompileVirtualFile;

        int i = 0;
        for (File toRecompileFile : toRecompileFiles) {
          toRecompileVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(toRecompileFile);
          toRecompileVirtualFiles[i] = toRecompileVirtualFile;
          i++;
        }

        CompilerMessage[] compilerMessages = processHandler.getCompilerMessages().toArray(new CompilerMessage[0]);

        for (final CompilerMessage compileMessage : compilerMessages) {
          final CompilerMessageCategory category;
          category = getMessageCategory(compileMessage);

          String url = compileMessage.getUrl();


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
          compileContext.addMessage(category, compileMessage.getMessage(), url, compileMessage.getLinenum(), compileMessage.getColomnnum());

        }

        StringBuffer unparsedBuffer = processHandler.getUnparsedOutput();
        if (unparsedBuffer.length() != 0)
          compileContext.addMessage(CompilerMessageCategory.ERROR, unparsedBuffer.toString(), null, -1, -1);

        return new GroovyCompileExitStatus(processHandler.getSuccessfullyCompiled(), toRecompileVirtualFiles);

//        if (exitStatus == null) return new GroovyCompileExitStatus(new HashSet<OutputItem>(), VirtualFile.EMPTY_ARRAY);
      } catch (ExecutionException e) {
        e.printStackTrace();
      }
    }

    VirtualFile[] toRecompile = compiledItems.size() > 0 ?
        VirtualFile.EMPTY_ARRAY :
        allCompiling.toArray(new VirtualFile[allCompiling.size()]);

    return new GroovyCompileExitStatus(compiledItems, toRecompile);
  }


  static HashSet<String> required = new HashSet<String>();
  static {
    required.add("groovy");
    required.add("asm");
    required.add("antlr");
    required.add("junit");
  }

  private boolean required(String name)
  {
    name = name.toLowerCase();
    if (!name.endsWith(".jar"))
      return false;

    name = name.substring(0,name.indexOf('.'));
    int ind = name.lastIndexOf('-');
    if (ind!= -1 && name.length() > ind+1 && Character.isDigit(name.charAt(ind+1))) {
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
          printer.print(GroovycRunner.SRC_FILE);
        } else {
          printer.print(GroovycRunner.TEST_FILE);
        }
        printer.println();
        printer.print(item.getPath());
        printer.println();
      }

      //classpath
      printer.print(GroovycRunner.CLASSPATH);
      printer.println();
      printer.print(getCompilationClasspath(module).getPathsString());
      printer.println();

//output
      boolean forTestSourceFolders;
      String outputPath;

//ordinary classes
      forTestSourceFolders = false;
      outputPath = CompilerPaths.getModuleOutputPath(module, forTestSourceFolders);
      printer.print(GroovycRunner.OUTPUTPATH);
      printer.println();
      printer.print(outputPath);
      printer.println();

//test output
      forTestSourceFolders = true;
      outputPath = CompilerPaths.getModuleOutputPath(module, forTestSourceFolders);
      printer.print(GroovycRunner.TEST_OUTPUTPATH);
      printer.println();
      printer.print(outputPath);
      printer.println();

//module name
      printer.print(GroovycRunner.MODULE_NAME);
      printer.println();
      printer.print(module.getName());
      printer.println();

//source
      printer.print(GroovycRunner.SRC_FOLDERS);
      printer.println();
      printer.print(getNonExcludedModuleSourceFolders(module).getPathsString());
      printer.println();

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
        assert file != null;
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
