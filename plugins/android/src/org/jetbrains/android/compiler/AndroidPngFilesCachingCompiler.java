package org.jetbrains.android.compiler;

import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.android.compiler.tools.AndroidApt;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidPngFilesCachingCompiler implements SourceGeneratingCompiler {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.AndroidPngFilesCachingCompiler");

  @NotNull
  @Override
  public GenerationItem[] getGenerationItems(final CompileContext context) {
    return ApplicationManager.getApplication().runReadAction(new Computable<GenerationItem[]>() {
      @Override
      public GenerationItem[] compute() {
        final List<GenerationItem> items = new ArrayList<GenerationItem>();
        final Module[] modules = ModuleManager.getInstance(context.getProject()).getModules();

        for (Module module : modules) {
          final AndroidFacet facet = AndroidFacet.getInstance(module);
          if (facet == null) {
            continue;
          }

          final VirtualFile resourcesDir = AndroidAptCompiler.getResourceDirForApkCompiler(facet);
          if (resourcesDir == null) {
            continue;
          }

          final AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
          if (platform == null) {
            context.addMessage(CompilerMessageCategory.ERROR,
                               AndroidBundle.message("android.compilation.error.specify.platform", module.getName()), null, -1, -1);
            continue;
          }

          final int platformToolsRevision = platform.getSdk().getPlatformToolsRevision();
          if (platformToolsRevision > 0 && platformToolsRevision <= 7) {
            // png files cache is supported since platform-tools-r8
            continue;
          }

          items.add(new MyItem(module, platform.getTarget(), resourcesDir));
        }
        return items.toArray(new GenerationItem[items.size()]);
      }
    });
  }

  @Override
  public GenerationItem[] generate(CompileContext context,
                                   GenerationItem[] items,
                                   VirtualFile outputRootDirectory) {
    if (items == null || items.length == 0 || !AndroidCompileUtil.isFullBuild(context)) {
      return new GenerationItem[0];
    }

    context.getProgressIndicator().setText("Processing PNG files...");

    final List<GenerationItem> processedItems = new ArrayList<GenerationItem>();

    for (GenerationItem GenerationItem : items) {
      final MyItem item = (MyItem)GenerationItem;
      final Module module = item.getModule();
      
      if (!AndroidCompileUtil.isModuleAffected(context, module)) {
        continue;
      }

      final String resDirOsPath = FileUtil.toSystemDependentName(item.getResourceDir().getPath());

      try {
        final String resCacheDirOsPath = AndroidCompileUtil.findResourcesCacheDirectory(module, true, context);
        if (resCacheDirOsPath == null) {
          continue;
        }

        final Map<CompilerMessageCategory, List<String>> messages = AndroidCompileUtil.toCompilerMessageCategoryKeys(
          AndroidApt.crunch(item.getTarget(), Collections.singletonList(resDirOsPath), resCacheDirOsPath));
        AndroidCompileUtil.addMessages(context, messages, null);
        if (context.getMessages(CompilerMessageCategory.ERROR).length == 0) {
          processedItems.add(item);
        }
      }
      catch (IOException e) {
        final String message = e.getMessage();
        if (message != null) {
          LOG.info(e);
          context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
        }
        else {
          LOG.error(e);
        }
      }
    }

    return processedItems.toArray(new GenerationItem[processedItems.size()]);
  }

  @Nullable
  @Override
  public VirtualFile getPresentableFile(CompileContext context, Module module, VirtualFile outputRoot, VirtualFile generatedFile) {
    return null;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Android PNG files caching compiler";
  }

  @Override
  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  @Override
  public ValidityState createValidityState(DataInput in) throws IOException {
    return new MyValidityState(in);
  }

  private static class MyItem implements GenerationItem {
    private final Module myModule;
    private final IAndroidTarget myTarget;
    private final VirtualFile myResourceDir;

    private MyItem(@NotNull Module module,
                   @NotNull IAndroidTarget target,
                   @NotNull VirtualFile resourceDir) {
      myModule = module;
      myTarget = target;
      myResourceDir = resourceDir;
    }

    @NotNull
    public IAndroidTarget getTarget() {
      return myTarget;
    }

    @NotNull
    public VirtualFile getResourceDir() {
      return myResourceDir;
    }

    @NotNull
    public Module getModule() {
      return myModule;
    }

    @Override
    public boolean isTestSource() {
      return false;
    }

    @Nullable
    @Override
    public String getPath() {
      return null;
    }

    @Override
    public ValidityState getValidityState() {
      return new MyValidityState(myTarget, myResourceDir);
    }
  }

  private static class MyValidityState implements ValidityState {
    private final String myTargetHashString;
    private final Map<String, Long> myTimestamps = new HashMap<String, Long>();

    private MyValidityState(@NotNull IAndroidTarget target, @NotNull VirtualFile resourceDir) {
      myTargetHashString = target.hashString();
      collectPngFiles(resourceDir, new HashSet<VirtualFile>());
    }

    public MyValidityState(@NotNull DataInput in) throws IOException {
      myTargetHashString = in.readUTF();

      final int pairsCount = in.readInt();
      for (int i = 0; i < pairsCount; i++) {
        final String path = in.readUTF();
        final long timestamp = in.readLong();
        myTimestamps.put(path, timestamp);
      }
    }

    private void collectPngFiles(@NotNull VirtualFile file, @NotNull Set<VirtualFile> visited) {
      if (!visited.add(file)) {
        return;
      }

      if (file.isDirectory()) {
        for (VirtualFile child : file.getChildren()) {
          collectPngFiles(child, visited);
        }
      }
      else if (AndroidUtils.PNG_EXTENSION.equals(file.getExtension())) {
        myTimestamps.put(file.getPath(), file.getTimeStamp());
      }
    }

    @Override
    public boolean equalsTo(ValidityState otherState) {
      if (!(otherState instanceof MyValidityState)) {
        return false;
      }

      final MyValidityState other = (MyValidityState)otherState;

      if (!Comparing.equal(myTargetHashString, other.myTargetHashString)) {
        return false;
      }

      return myTimestamps.equals(other.myTimestamps);
    }

    @Override
    public void save(DataOutput out) throws IOException {
      out.writeUTF(myTargetHashString);

      out.writeInt(myTimestamps.size());
      for (Map.Entry<String, Long> e : myTimestamps.entrySet()) {
        out.writeUTF(e.getKey());
        out.writeLong(e.getValue());
      }
    }
  }
}
