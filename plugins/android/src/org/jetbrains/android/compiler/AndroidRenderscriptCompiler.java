package org.jetbrains.android.compiler;

import com.android.AndroidConstants;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.fileTypes.AndroidRenderscriptFileType;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.ExecutionUtil;
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
        final List<GenerationItem> items = new ArrayList<GenerationItem>(files.length);
        for (final VirtualFile file : files) {
          final Module module = context.getModuleByFile(file);
          final AndroidFacet facet = FacetManager.getInstance(module).getFacetByType(AndroidFacet.ID);
          if (facet != null) {
            final AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
            if (platform == null) {
              context.addMessage(CompilerMessageCategory.ERROR,
                                 AndroidBundle.message("android.compilation.error.specify.platform", module.getName()), null, -1, -1);
              continue;
            }

            final IAndroidTarget target = platform.getTarget();
            final String sdkLocation = platform.getSdk().getLocation();

            final String packageName = AndroidUtils.getPackageName(module, file);
            if (packageName == null) {
              context.addMessage(CompilerMessageCategory.ERROR, "Cannot compute package for file", file.getUrl(), -1, -1);
              continue;
            }

            final String resourceDirPath = AndroidRootUtil.getResourceDirPath(facet);
            assert resourceDirPath != null;

            addItem(context, file, facet, resourceDirPath, sdkLocation, target, packageName, items);

            if (facet.getConfiguration().LIBRARY_PROJECT) {
              final HashSet<Module> usingModules = new HashSet<Module>();
              AndroidUtils.collectModulesDependingOn(module, usingModules);

              for (final Module module1 : usingModules) {
                final AndroidFacet facet1 = AndroidFacet.getInstance(module1);
                if (facet1 != null) {
                  addItem(context, file, facet1, resourceDirPath, sdkLocation, target, packageName, items);
                }
              }
            }
          }
        }
        return items.toArray(new GenerationItem[items.size()]);
      }
    });
  }

  private static void addItem(@NotNull final CompileContext context,
                              @NotNull final VirtualFile sourceFile,
                              @NotNull final AndroidFacet facet,
                              @NotNull final String resourceDirPath,
                              @NotNull String sdkLocation,
                              @NotNull final IAndroidTarget target,
                              @NotNull final String packageName,
                              @NotNull final List<GenerationItem> items) {
    final Module module = facet.getModule();
    final String sourceRootPath = AndroidRootUtil.getRenderscriptGenSourceRootPath(module);
    if (sourceRootPath == null) {
      return;
    }
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(context.getProject()).getFileIndex();
    final String rawDirPath = resourceDirPath + '/' + AndroidConstants.FD_RES_RAW;

    items.add(new MyGenerationItem(module, sourceFile, sourceRootPath, packageName, rawDirPath, fileIndex.isInTestSourceContent(sourceFile),
                                   sdkLocation, target));
  }

  @Override
  public GenerationItem[] generate(final CompileContext context,
                                   final GenerationItem[] items,
                                   final VirtualFile outputRootDirectory) {
    if (items == null || items.length <= 0) {
      return EMPTY_GENERATION_ITEM_ARRAY;
    }

    context.getProgressIndicator().setText("Compiling RenderScript files...");
    final GenerationItem[] generationItems = doGenerate(context, items);
    final Set<VirtualFile> generatedVFiles = new HashSet<VirtualFile>();
    final HashSet<VirtualFile> visited = new HashSet<VirtualFile>();

    for (GenerationItem item : generationItems) {
      final MyGenerationItem genItem = (MyGenerationItem)item;
      final File genDir = new File(genItem.myGenRootPath, genItem.myPackageName.replace('.', File.separatorChar));
      CompilerUtil.refreshIOFile(genDir);
      final VirtualFile generatedVFile = LocalFileSystem.getInstance().findFileByIoFile(genDir);
      if (generatedVFile != null) {
        collectFiles(generatedVFile, visited, generatedVFiles);
      }
    }
    if (context instanceof CompileContextEx) {
      ((CompileContextEx)context).markGenerated(generatedVFiles);
    }
    return generationItems;
  }

  private static void collectFiles(@NotNull VirtualFile root, @NotNull Set<VirtualFile> visited, @NotNull Set<VirtualFile> result) {
    if (!visited.add(root)) {
      return;
    }

    if (root.isDirectory()) {
      for (VirtualFile child : root.getChildren()) {
        collectFiles(child, visited, result);
      }
    }
    else {
      result.add(root);
    }
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

  private static GenerationItem[] doGenerate(@NotNull final CompileContext context, @NotNull final GenerationItem[] items) {
    if (context.getProject().isDisposed()) {
      return EMPTY_GENERATION_ITEM_ARRAY;
    }

    final List<GenerationItem> results = new ArrayList<GenerationItem>(items.length);
    for (final GenerationItem item : items) {
      if (item instanceof MyGenerationItem) {
        final MyGenerationItem genItem = (MyGenerationItem)item;

        if (!AndroidCompileUtil.isModuleAffected(context, genItem.myModule)) {
          continue;
        }

        try {
          final Map<CompilerMessageCategory, List<String>> messages = launchRenderscriptCompiler(context.getProject(),
                                                                                                 genItem.mySdkLocation,
                                                                                                 genItem.myAndroidTarget,
                                                                                                 genItem.mySourceFile,
                                                                                                 genItem.myGenRootPath,
                                                                                                 genItem.myRawDirPath);
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              if (context.getProject().isDisposed()) {
                return;
              }
              addMessages(context, messages, genItem.mySourceFile.getUrl());
            }
          });

          if (messages.get(CompilerMessageCategory.ERROR).isEmpty()) {
            results.add(genItem);
          }
        }
        catch (final IOException e) {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              if (context.getProject().isDisposed()) return;
              context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), genItem.mySourceFile.getUrl(), -1, -1);
            }
          });
        }
      }
    }
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

  private static Map<CompilerMessageCategory, List<String>> launchRenderscriptCompiler(@NotNull Project project,
                                                                                       @NotNull final String sdkLocation,
                                                                                       @NotNull IAndroidTarget target,
                                                                                       @NotNull final VirtualFile sourceFile,
                                                                                       @NotNull final String genFolderPath,
                                                                                       @NotNull final String rawDirPath)
    throws IOException {
    final List<String> command = new ArrayList<String>();
    command.add(
      FileUtil.toSystemDependentName(sdkLocation + '/' + SdkConstants.OS_SDK_PLATFORM_TOOLS_FOLDER + SdkConstants.FN_RENDERSCRIPT));
    command.add("-I");
    command.add(target.getPath(IAndroidTarget.ANDROID_RS_CLANG));
    command.add("-I");
    command.add(target.getPath(IAndroidTarget.ANDROID_RS));
    command.add("-p");
    command.add(FileUtil.toSystemDependentName(genFolderPath));
    command.add("-o");
    command.add(FileUtil.toSystemDependentName(rawDirPath));

    final String sourceFilePath = FileUtil.toSystemDependentName(sourceFile.getPath());

    final VirtualFile genFolder = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(genFolderPath));
    if (genFolder != null) {
      final String dependencyFolderPath = getDependencyFolder(project, sourceFile, genFolder);
      if (dependencyFolderPath != null) {
        command.add("-d");
        command.add(FileUtil.toSystemDependentName(dependencyFolderPath));
      }
    }

    command.add("-MD");
    command.add(sourceFilePath);

    LOG.info(AndroidUtils.command2string(command));
    return ExecutionUtil.execute(ArrayUtil.toStringArray(command));
  }

  @Nullable
  private static String getDependencyFolder(@NotNull final Project project,
                                            @NotNull final VirtualFile sourceFile,
                                            @NotNull final VirtualFile genFolder) {
    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();

    final VirtualFile sourceRoot = index.getSourceRootForFile(sourceFile);
    if (sourceRoot == null) {
      return null;
    }

    final VirtualFile parent = sourceFile.getParent();
    if (parent == sourceRoot) {
      return genFolder.getPath();
    }

    final String relativePath = VfsUtilCore.getRelativePath(sourceFile.getParent(), sourceRoot, '/');
    assert relativePath != null;
    return genFolder.getPath() + '/' + relativePath;
  }

  private static class MyGenerationItem implements GenerationItem {
    final Module myModule;
    final String mySdkLocation;
    final VirtualFile mySourceFile;
    final boolean myTestSource;
    final IAndroidTarget myAndroidTarget;
    final String myGenRootPath;
    final String myRawDirPath;
    final String myPackageName;
    final boolean myFileExists;
    final File myParentDirectory;

    public MyGenerationItem(@NotNull Module module,
                            @NotNull VirtualFile sourceFile,
                            @NotNull String genRootPath,
                            @NotNull String packageName,
                            @NotNull String rawDirPath,
                            boolean testSource,
                            @NotNull String sdkLocation,
                            @NotNull IAndroidTarget target) {
      myModule = module;
      mySourceFile = sourceFile;
      myRawDirPath = rawDirPath;
      myTestSource = testSource;
      mySdkLocation = sdkLocation;
      myAndroidTarget = target;
      myGenRootPath = genRootPath;
      myPackageName = packageName;
      myParentDirectory = new File(myGenRootPath, myPackageName.replace('.', File.separatorChar));
      myFileExists = myParentDirectory.exists();
    }

    @Nullable
    public String getPath() {
      return null;
    }

    @Nullable
    public ValidityState getValidityState() {
      return new MyValidityState(mySourceFile, myFileExists, myRawDirPath);
    }

    public Module getModule() {
      return myModule;
    }

    public boolean isTestSource() {
      return myTestSource;
    }
  }
  
  private static class MyValidityState implements ValidityState {
    private final long myTimestamp;
    private final long myRawDirTimestamp;
    private final boolean myFileExists;
    
    public MyValidityState(@NotNull DataInput in) throws IOException {
      myTimestamp = in.readLong();
      myRawDirTimestamp = in.readLong();
      myFileExists = true;
    }

    public MyValidityState(@NotNull VirtualFile file, boolean fileExists, @NotNull String rawDirPath) {
      myTimestamp = file.getTimeStamp();
      myFileExists = fileExists;
      myRawDirTimestamp = new File(rawDirPath).lastModified();
    }

    @Override
    public boolean equalsTo(ValidityState otherState) {
      if (!(otherState instanceof MyValidityState)) {
        return false;
      }

      final MyValidityState st = (MyValidityState)otherState;
      return myTimestamp == st.myTimestamp &&
             myRawDirTimestamp == st.myRawDirTimestamp &&
             myFileExists == st.myFileExists;
    }

    @Override
    public void save(DataOutput out) throws IOException {
      out.writeLong(myTimestamp);
      out.writeLong(myRawDirTimestamp);
    }
  }
}
