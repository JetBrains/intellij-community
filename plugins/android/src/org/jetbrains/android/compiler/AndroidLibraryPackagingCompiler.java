package org.jetbrains.android.compiler;

import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLibraryPackagingCompiler implements ClassPostProcessingCompiler {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.AndroidLibraryPackagingCompiler");

  @NotNull
  @Override
  public ProcessingItem[] getProcessingItems(final CompileContext context) {
    return ApplicationManager.getApplication().runReadAction(new Computable<ProcessingItem[]>() {
      @Override
      public ProcessingItem[] compute() {
        final List<ProcessingItem> result = new ArrayList<ProcessingItem>();
        
        for (Module module : ModuleManager.getInstance(context.getProject()).getModules()) {
          final AndroidFacet facet = AndroidFacet.getInstance(module);
          if (facet == null || !facet.getConfiguration().LIBRARY_PROJECT) {
            continue;
          }
          
          final CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
          LOG.assertTrue(extension != null);
          
          final VirtualFile outputDir = extension.getCompilerOutputPath();
          if (outputDir == null) {
            continue;
          }
          final HashSet<VirtualFile> firstPackageDirs = new HashSet<VirtualFile>();
          AndroidDexCompiler.addModuleOutputDir(firstPackageDirs, outputDir);
          
          if (firstPackageDirs.size() == 0) {
            continue;
          }

          final VirtualFile outputDirectory = AndroidDexCompiler.getOutputDirectoryForDex(module);
          if (outputDirectory == null) {
            LOG.error("Cannot find output directory for dex");
            continue;
          }
          
          result.add(new MyProcessingItem(module, firstPackageDirs.toArray(new VirtualFile[firstPackageDirs.size()]), outputDir));
        }
        return result.toArray(new ProcessingItem[result.size()]);
      }
    });
  }

  @Override
  public ProcessingItem[] process(CompileContext context, ProcessingItem[] items) {
    if (!AndroidCompileUtil.isFullBuild(context) || items == null || items.length == 0) {
      return ProcessingItem.EMPTY_ARRAY;
    }

    context.getProgressIndicator().setText("Packaging library modules...");
    final List<MyProcessingItem> result = new ArrayList<MyProcessingItem>();
    
    for (ProcessingItem item : items) {
      final MyProcessingItem processingItem = (MyProcessingItem)item;
      
      if (!AndroidCompileUtil.isModuleAffected(context, processingItem.getModule())) {
        continue;
      }

      final String[] classesDirOsPaths = AndroidCompileUtil.toOsPaths(processingItem.getClassDirectories());
      final String outputJarOsPath = FileUtil.toSystemDependentName(processingItem.getOutputDirectory().getPath() + '/' +
                                                                    AndroidCompileUtil.CLASSES_JAR_FILE_NAME);

      final File outputJarFile = new File(outputJarOsPath);
      try {
        AndroidCompileUtil.packClassFilesIntoJar(ArrayUtil.EMPTY_STRING_ARRAY, classesDirOsPaths, outputJarFile);
        CompilerUtil.refreshIOFile(outputJarFile);
        result.add(processingItem);
      }
      catch (IOException e) {
        if (e.getMessage() == null) {
          LOG.error(e);
        }
        else {
          LOG.info(e);
          context.addMessage(CompilerMessageCategory.ERROR, "Cannot pack sources of module " +
                                                            processingItem.getModule().getName() +
                                                            " to " +
                                                            outputJarFile.getName() +
                                                            ": " +
                                                            e.getMessage(), null, -1, -1);
        }
      }
    }
    return result.toArray(new MyProcessingItem[result.size()]);
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Android Library Packaging Compiler";
  }

  @Override
  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  @Override
  public ValidityState createValidityState(DataInput in) throws IOException {
    return new ClassesAndJarsValidityState(in);
  }
  
  private static class MyProcessingItem implements ProcessingItem {
    private final Module myModule;
    private final VirtualFile[] myClassDirectories;
    private final VirtualFile myOutputDirectory;

    private MyProcessingItem(@NotNull Module module, @NotNull VirtualFile[] classDirectories, @NotNull VirtualFile directory) {
      assert classDirectories.length > 0;
      myOutputDirectory = directory;
      myClassDirectories = classDirectories;
      myModule = module;
    }

    @NotNull
    @Override
    public VirtualFile getFile() {
      return myClassDirectories[0].getParent();
    }

    @Override
    public ValidityState getValidityState() {
      return new ClassesAndJarsValidityState(Arrays.asList(myClassDirectories));
    }
                               
    @NotNull
    public Module getModule() {
      return myModule;
    }

    @NotNull
    public VirtualFile[] getClassDirectories() {
      return myClassDirectories;
    }

    @NotNull
    public VirtualFile getOutputDirectory() {
      return myOutputDirectory;
    }
  }
}
