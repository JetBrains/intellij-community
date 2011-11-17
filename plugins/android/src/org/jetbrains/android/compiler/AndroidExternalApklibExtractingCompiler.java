package org.jetbrains.android.compiler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import com.intellij.util.io.ZipUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.maven.AndroidExternalApklibDependenciesManager;
import org.jetbrains.android.maven.AndroidMavenUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidExternalApklibExtractingCompiler implements SourceGeneratingCompiler {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.AndroidExternalApklibExtractingCompiler");
  
  @Nullable
  @Override
  public VirtualFile getPresentableFile(CompileContext context, Module module, VirtualFile outputRoot, VirtualFile generatedFile) {
    return null;
  }

  @Override
  public GenerationItem[] getGenerationItems(final CompileContext context) {
    return ApplicationManager.getApplication().runReadAction(new Computable<GenerationItem[]>() {
      @Override
      public GenerationItem[] compute() {
        final List<GenerationItem> result = new ArrayList<GenerationItem>();

        for (Module module : ModuleManager.getInstance(context.getProject()).getModules()) {
          final AndroidFacet facet = AndroidFacet.getInstance(module);
          if (facet == null || !facet.getConfiguration().LIBRARY_PROJECT) {
            continue;
          }

          final String mavenIdStr = AndroidMavenUtil.getMavenIdStringByExtApklibModule(facet.getModule());
          if (mavenIdStr == null) {
            continue;
          }

          final AndroidExternalApklibDependenciesManager manager =
            AndroidExternalApklibDependenciesManager.getInstance(context.getProject());
          final String artifactFilePath = manager.getArtifactFilePath(mavenIdStr);
          
          if (artifactFilePath == null) {
            context.addMessage(CompilerMessageCategory.ERROR,
                               "Cannot find artifact file for generated module " + module.getName() + ". Try to force reimport Maven model",
                               null, -1, -1);
            continue;
          }

          final VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
          if (roots.length == 0) {
            context.addMessage(CompilerMessageCategory.ERROR, "Cannot find any content root in generated module " +
                                                              module.getName() +
                                                              ". Try to force reimport Maven model", null, -1, -1);
            continue;
          }

          final VirtualFile root = roots[0];

          result.add(new MyGenerationItem(module, FileUtil.toSystemDependentName(root.getPath()),
                                          FileUtil.toSystemDependentName(artifactFilePath)));
        }

        return result.toArray(new GenerationItem[result.size()]);
      }
    });
  }

  @Override
  public GenerationItem[] generate(CompileContext context,
                                   GenerationItem[] items,
                                   VirtualFile outputRootDirectory) {
    if (items == null || items.length == 0) {
      return new GenerationItem[0];
    }

    context.getProgressIndicator().setText(AndroidBundle.message("android.compile.messages.processing.external.apklib.dependencies"));

    final GenerationItem[] generatedItems = doGenerate(context, items);
    final Set<VirtualFile> generatedVFiles = new HashSet<VirtualFile>();
    final HashSet<VirtualFile> visited = new HashSet<VirtualFile>();

    for (GenerationItem item : generatedItems) {
      final MyGenerationItem genItem = (MyGenerationItem)item;

      final File srcRoot = new File(genItem.getGenContentRootPath() + '/' + 
                                    AndroidMavenUtil.APK_LIB_ARTIFACT_SOURCE_ROOT);
      
      final VirtualFile vSrcRoot = LocalFileSystem.getInstance().findFileByIoFile(srcRoot);
      if (vSrcRoot != null) {
        vSrcRoot.getParent().refresh(false, true);
        AndroidUtils.collectFiles(vSrcRoot, visited, generatedVFiles);
      }
    }
    
    if (context instanceof CompileContextEx) {
      ((CompileContextEx)context).markGenerated(generatedVFiles);
    }
    
    return generatedItems;
  }

  private static GenerationItem[] doGenerate(CompileContext context, GenerationItem[] items) {
    final List<GenerationItem> result = new ArrayList<GenerationItem>();

    for (GenerationItem item : items) {
      final Module module = item.getModule();

      if (!AndroidCompileUtil.isModuleAffected(context, module)) {
        continue;
      }

      final MyGenerationItem genItem = (MyGenerationItem)item;

      final String artifactPath = genItem.getArtifactPath();
      final File artifactFile = new File(artifactPath);

      if (!artifactFile.exists()) {
        context.addMessage(CompilerMessageCategory.ERROR, "Cannot find file " + artifactPath, null, -1, -1);
        continue;
      }

      if (!artifactFile.isFile()) {
        context.addMessage(CompilerMessageCategory.ERROR, artifactPath + " is not file", null, -1, -1);
        continue;
      }

      final String genContentRootPath = ((MyGenerationItem)item).getGenContentRootPath();
      final File genContentRootFile = new File(genContentRootPath);

      if (genContentRootFile.exists()) {
        if (!FileUtil.delete(genContentRootFile)) {
          context.addMessage(CompilerMessageCategory.ERROR, "Cannot delete old directory: " + genContentRootPath, null, -1, -1);
          continue;
        }
      }
      
      if (!genContentRootFile.mkdir()) {
        context.addMessage(CompilerMessageCategory.ERROR, "Cannot create directory " + genContentRootPath, null, -1, -1);
        continue;
      }

      try {
        ZipUtil.extract(artifactFile, genContentRootFile, null);
      }
      catch (IOException e) {
        final String message = e.getMessage();
        if (message == null) {
          context.addMessage(CompilerMessageCategory.ERROR, "Unknown I/O error", null, -1, -1);
          LOG.error(e);
        }
        else {
          context.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
          LOG.info(e);
        }
        continue;
      }

      result.add(genItem);
    }

    return result.toArray(new GenerationItem[result.size()]);
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Android External Apklib Extracting Compiler";
  }

  @Override
  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  @Override
  public ValidityState createValidityState(DataInput in) throws IOException {
    return TimestampValidityState.load(in);
  }

  private static class MyGenerationItem implements GenerationItem {
    private final Module myModule;
    private final String myGenContentRootPath;
    private final String myArtifactPath;

    private MyGenerationItem(@NotNull Module module, @NotNull String genContentRootPath, @NotNull String artifactPath) {
      myModule = module;
      myGenContentRootPath = genContentRootPath;
      myArtifactPath = artifactPath;
    }

    @Nullable
    @Override
    public String getPath() {
      return myGenContentRootPath;
    }

    @Override
    public ValidityState getValidityState() {
      return new TimestampValidityState(new File(myArtifactPath).lastModified());
    }

    @Override
    @NotNull
    public Module getModule() {
      return myModule;
    }

    @Override
    public boolean isTestSource() {
      return false;
    }

    @NotNull
    public String getGenContentRootPath() {
      return myGenContentRootPath;
    }

    @NotNull
    public String getArtifactPath() {
      return myArtifactPath;
    }
  }
}
