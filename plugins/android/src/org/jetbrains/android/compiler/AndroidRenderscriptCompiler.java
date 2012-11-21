package org.jetbrains.android.compiler;


import com.android.SdkConstants;
import com.android.sdklib.IAndroidTarget;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.compiler.tools.AndroidRenderscript;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.fileTypes.AndroidRenderscriptFileType;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidRenderscriptCompiler implements SourceGeneratingCompiler {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.AndroidRenderscriptCompiler");

  private static final GenerationItem[] EMPTY_GENERATION_ITEM_ARRAY = {};

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
        if (context.getProject().isDisposed()) {
          return EMPTY_GENERATION_ITEM_ARRAY;
        }

        final VirtualFile[] files = context.getProjectCompileScope().getFiles(AndroidRenderscriptFileType.INSTANCE, true);
        final Map<Module, Collection<VirtualFile>> module2files = new HashMap<Module, Collection<VirtualFile>>();

        for (VirtualFile file : files) {
          final Module module = context.getModuleByFile(file);

          if (module != null) {
            Collection<VirtualFile> filesForModule = module2files.get(module);

            if (filesForModule == null) {
              filesForModule = new ArrayList<VirtualFile>();
              module2files.put(module, filesForModule);
            }

            filesForModule.add(file);
          }
        }
        final List<GenerationItem> items = new ArrayList<GenerationItem>(files.length);

        for (Map.Entry<Module, Collection<VirtualFile>> entry : module2files.entrySet()) {
          final Module module = entry.getKey();
          final AndroidFacet facet = FacetManager.getInstance(module).getFacetByType(AndroidFacet.ID);
          if (facet == null) {
            continue;
          }

          final AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
          if (platform == null) {
            context.addMessage(CompilerMessageCategory.ERROR,
                               AndroidBundle.message("android.compilation.error.specify.platform", module.getName()), null, -1, -1);
            continue;
          }

          final IAndroidTarget target = platform.getTarget();
          final String sdkLocation = platform.getSdkData().getLocation();

          final String resourceDirPath = AndroidRootUtil.getResourceDirPath(facet);
          assert resourceDirPath != null;

          addItem(entry.getValue(), facet, resourceDirPath, sdkLocation, target, items);
        }
        return items.toArray(new GenerationItem[items.size()]);
      }
    });
  }

  private static void addItem(@NotNull final Collection<VirtualFile> sourceFiles,
                              @NotNull final AndroidFacet facet,
                              @NotNull final String resourceDirPath,
                              @NotNull String sdkLocation,
                              @NotNull final IAndroidTarget target,
                              @NotNull final List<GenerationItem> items) {
    final String sourceRootPath = AndroidRootUtil.getRenderscriptGenSourceRootPath(facet);
    if (sourceRootPath == null) {
      return;
    }
    final String rawDirPath = resourceDirPath + '/' + SdkConstants.FD_RES_RAW;
    items.add(new MyGenerationItem(facet.getModule(), sourceFiles, rawDirPath, sdkLocation, target));
  }

  @Override
  public GenerationItem[] generate(final CompileContext context,
                                   final GenerationItem[] items,
                                   final VirtualFile outputRootDirectory) {
    if (items == null || items.length <= 0) {
      return EMPTY_GENERATION_ITEM_ARRAY;
    }

    context.getProgressIndicator().setText("Compiling RenderScript files...");
    final GenerationItem[] generationItems = doGenerate(context, items, outputRootDirectory);
    final Set<VirtualFile> generatedVFiles = new HashSet<VirtualFile>();
    final HashSet<VirtualFile> visited = new HashSet<VirtualFile>();

    outputRootDirectory.refresh(false, true);
    AndroidUtils.collectFiles(outputRootDirectory, visited, generatedVFiles);

    if (context instanceof CompileContextEx) {
      ((CompileContextEx)context).markGenerated(generatedVFiles);
    }
    return generationItems;
  }

  @NotNull
  @Override
  public String getDescription() {
    return AndroidBundle.message("android.renderscript.compiler.description");
  }

  @Override
  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  @Nullable
  @Override
  public ValidityState createValidityState(DataInput in) throws IOException {
    return new MyValidityState(in);
  }

  private static GenerationItem[] doGenerate(@NotNull final CompileContext context,
                                             @NotNull final GenerationItem[] items,
                                             VirtualFile outputRootDirectory) {
    if (context.getProject().isDisposed()) {
      return EMPTY_GENERATION_ITEM_ARRAY;
    }

    // we have one item per module there, so clear output directory
    final String genRootPath = FileUtil.toSystemDependentName(outputRootDirectory.getPath());
    final File genRootDir = new File(genRootPath);
    if (genRootDir.exists()) {
      if (!FileUtil.delete(genRootDir)) {
        context.addMessage(CompilerMessageCategory.ERROR, "Cannot delete directory " + genRootPath, null, -1, -1);
        return EMPTY_GENERATION_ITEM_ARRAY;
      }
      if (!genRootDir.mkdir()) {
        context.addMessage(CompilerMessageCategory.ERROR, "Cannot create directory " + genRootPath, null, -1, -1);
        return EMPTY_GENERATION_ITEM_ARRAY;
      }
    }

    final List<GenerationItem> results = new ArrayList<GenerationItem>(items.length);
    for (final GenerationItem item : items) {
      if (item instanceof MyGenerationItem) {
        final MyGenerationItem genItem = (MyGenerationItem)item;

        if (!AndroidCompileUtil.isModuleAffected(context, genItem.myModule)) {
          continue;
        }

        boolean success = true;

        for (final VirtualFile sourceFile : genItem.myFiles) {
          final String depFolderOsPath = getDependencyFolder(context.getProject(), sourceFile, outputRootDirectory);

          try {
            final Map<CompilerMessageCategory, List<String>> messages = AndroidCompileUtil.toCompilerMessageCategoryKeys(
              AndroidRenderscript
                .execute(genItem.mySdkLocation, genItem.myAndroidTarget, sourceFile.getPath(), genRootPath,
                         depFolderOsPath,
                         genItem.myRawDirPath));
            AndroidCompileUtil.markDirty(outputRootDirectory, true);

            ApplicationManager.getApplication().runReadAction(new Runnable() {
              public void run() {
                if (context.getProject().isDisposed()) {
                  return;
                }
                addMessages(context, messages, sourceFile.getUrl());
              }
            });

            if (messages.get(CompilerMessageCategory.ERROR).size() > 0) {
              success = false;
            }
          }
          catch (final IOException e) {
            LOG.info(e);
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              public void run() {
                if (context.getProject().isDisposed()) return;
                context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), sourceFile.getUrl(), -1, -1);
              }
            });
            success = false;
          }
        }

        if (success) {
          results.add(genItem);
        }
      }
    }
    outputRootDirectory.refresh(false, true);
    return results.toArray(new GenerationItem[results.size()]);
  }

  private static void addMessages(@NotNull final CompileContext context,
                                  @NotNull final Map<CompilerMessageCategory, List<String>> messages,
                                  @NotNull final String url) {
    for (final CompilerMessageCategory category : messages.keySet()) {
      final List<String> messageList = messages.get(category);
      for (final String message : messageList) {
        context.addMessage(category, message, url, -1, -1);
      }
    }
  }

  @Nullable
  static String getDependencyFolder(@NotNull final Project project,
                                    @NotNull final VirtualFile sourceFile,
                                    @NotNull final VirtualFile genFolder) {
    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();

    final VirtualFile sourceRoot = index.getSourceRootForFile(sourceFile);
    if (sourceRoot == null) {
      return null;
    }

    final VirtualFile parent = sourceFile.getParent();
    if (Comparing.equal(parent, sourceRoot)) {
      return genFolder.getPath();
    }

    final String relativePath = VfsUtilCore.getRelativePath(sourceFile.getParent(), sourceRoot, '/');
    assert relativePath != null;
    return genFolder.getPath() + '/' + relativePath;
  }

  private static class MyGenerationItem implements GenerationItem {
    final Module myModule;
    final String mySdkLocation;
    final Collection<VirtualFile> myFiles;
    final IAndroidTarget myAndroidTarget;
    final String myRawDirPath;
    private final MyValidityState myValidityState;

    public MyGenerationItem(@NotNull Module module,
                            @NotNull Collection<VirtualFile> files,
                            @NotNull String rawDirPath,
                            @NotNull String sdkLocation,
                            @NotNull IAndroidTarget target) {
      myModule = module;
      myFiles = files;
      myRawDirPath = rawDirPath;
      mySdkLocation = sdkLocation;
      myAndroidTarget = target;
      myValidityState = new MyValidityState(myFiles);
    }

    @Nullable
    public String getPath() {
      return "";
    }

    @Nullable
    public ValidityState getValidityState() {
      return myValidityState;
    }

    public Module getModule() {
      return myModule;
    }

    public boolean isTestSource() {
      return false;
    }
  }

  private static class MyValidityState implements ValidityState {
    private final Map<String, Long> myTimestamps = new HashMap<String, Long>();

    MyValidityState(DataInput in) throws IOException {
      final int size = in.readInt();

      for (int i = 0; i < size; i++) {
        final String path = in.readUTF();
        final long timestamp = in.readLong();

        myTimestamps.put(path, timestamp);
      }
    }

    MyValidityState(@NotNull Collection<VirtualFile> files) {
      for (VirtualFile file : files) {
        myTimestamps.put(file.getPath(), file.getTimeStamp());
      }
    }

    @Override
    public boolean equalsTo(ValidityState otherState) {
      if (!(otherState instanceof MyValidityState)) {
        return false;
      }
      return ((MyValidityState)otherState).myTimestamps.equals(myTimestamps);
    }

    @Override
    public void save(DataOutput out) throws IOException {
      out.writeInt(myTimestamps.size());

      for (Map.Entry<String, Long> entry : myTimestamps.entrySet()) {
        out.writeUTF(entry.getKey());
        out.writeLong(entry.getValue());
      }
    }
  }
}
