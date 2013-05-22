/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
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

package org.jetbrains.android.facet;

import com.android.resources.ResourceFolderType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.*;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.android.compiler.AndroidAptCompiler;
import org.jetbrains.android.compiler.AndroidAutogeneratorMode;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.fileTypes.AndroidIdlFileType;
import org.jetbrains.android.fileTypes.AndroidRenderscriptFileType;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.android.util.AndroidUtils.findSourceRoot;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 31, 2009
 * Time: 4:49:30 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidResourceFilesListener extends VirtualFileAdapter implements Disposable {
  private static final Key<String> CACHED_PACKAGE_KEY = Key.create("ANDROID_RESOURCE_LISTENER_CACHED_PACKAGE");

  private final MergingUpdateQueue myQueue;
  private final Project myProject;

  public AndroidResourceFilesListener(@NotNull Project project) {
    myProject = project;
    myQueue = new MergingUpdateQueue("AndroidResourcesCompilationQueue", 300, true, null, this, null, false);
  }

  @Override
  public void fileCreated(VirtualFileEvent event) {
    fileChanged(event);
  }

  @Override
  public void fileDeleted(VirtualFileEvent event) {
    fileChanged(event);
  }

  @Override
  public void fileMoved(VirtualFileMoveEvent event) {
    fileChanged(event);
  }

  @Override
  public void fileCopied(VirtualFileCopyEvent event) {
    fileChanged(event);
  }

  @Override
  public void contentsChanged(VirtualFileEvent event) {
    fileChanged(event);
  }

  private void fileChanged(@NotNull final VirtualFileEvent e) {
    myQueue.queue(new MyUpdate(e));
  }

  @Override
  public void dispose() {
  }

  public static void notifyFacetInitialized(@NotNull final AndroidFacet facet) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        final Manifest manifest = facet.getManifest();

        if (manifest != null) {
          facet.putUserData(CACHED_PACKAGE_KEY, manifest.getPackage().getValue());
        }
      }
    });
  }

  private class MyUpdate extends Update {
    private final VirtualFileEvent myEvent;

    public MyUpdate(VirtualFileEvent event) {
      super(event.getParent());
      myEvent = event;
    }

    @Override
    public void run() {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        return;
      }

      final Pair<Module, List<AndroidAutogeneratorMode>> pair =
        ApplicationManager.getApplication().runReadAction(new Computable<Pair<Module, List<AndroidAutogeneratorMode>>>() {
          @Override
          @Nullable
          public Pair<Module, List<AndroidAutogeneratorMode>> compute() {
            return computeCompilersToRunAndInvalidateLocalAttributesMap();
          }
        });

      if (pair == null) {
        return;
      }
      final Module module = pair.getFirst();

      for (AndroidAutogeneratorMode autogenerationMode : pair.getSecond()) {
        AndroidCompileUtil.generate(module, autogenerationMode);
      }
    }

    @Nullable
    private Pair<Module, List<AndroidAutogeneratorMode>> computeCompilersToRunAndInvalidateLocalAttributesMap() {
      if (myProject.isDisposed()) {
        return null;
      }
      final VirtualFile file = myEvent.getFile();
      final Module module = ModuleUtilCore.findModuleForFile(file, myProject);

      if (module == null || module.isDisposed()) {
        return null;
      }
      final AndroidFacet facet = AndroidFacet.getInstance(module);

      if (facet == null) {
        return null;
      }
      final VirtualFile parent = myEvent.getParent();

      if (parent == null) {
        return null;
      }
      final VirtualFile gp = parent.getParent();
      final VirtualFile resourceDir = AndroidRootUtil.getResourceDir(facet);

      if (Comparing.equal(gp, resourceDir) &&
          ResourceFolderType.VALUES.getName().equals(AndroidCommonUtils.getResourceTypeByDirName(parent.getName()))) {
        facet.getLocalResourceManager().invalidateAttributeDefinitions();
      }
      final VirtualFile manifestFile = AndroidRootUtil.getManifestFile(facet);
      final List<AndroidAutogeneratorMode> modes = new ArrayList<AndroidAutogeneratorMode>();

      if (Comparing.equal(manifestFile, file)) {
        if (AndroidAptCompiler.isToCompileModule(module, facet.getConfiguration())) {
          final Manifest manifest = facet.getManifest();
          final String aPackage = manifest != null ? manifest.getPackage().getValue() : null;
          final String cachedPackage = facet.getUserData(CACHED_PACKAGE_KEY);

          if (cachedPackage != null && !cachedPackage.equals(aPackage)) {
            String aptGenDirPath = AndroidRootUtil.getAptGenSourceRootPath(facet);
            AndroidCompileUtil.removeDuplicatingClasses(module, cachedPackage, AndroidUtils.R_CLASS_NAME, null, aptGenDirPath);
          }
          facet.putUserData(CACHED_PACKAGE_KEY, aPackage);
          modes.add(AndroidAutogeneratorMode.AAPT);
        }
        modes.add(AndroidAutogeneratorMode.BUILDCONFIG);
      }
      else if (file.getFileType() == AndroidIdlFileType.ourFileType) {
        VirtualFile sourceRoot = findSourceRoot(module, file);
        if (sourceRoot != null && !Comparing.equal(AndroidRootUtil.getAidlGenDir(facet), sourceRoot)) {
          modes.add(AndroidAutogeneratorMode.AIDL);
        }
      }
      else if (file.getFileType() == AndroidRenderscriptFileType.INSTANCE) {
        final VirtualFile sourceRoot = findSourceRoot(module, file);
        if (sourceRoot != null && !Comparing.equal(AndroidRootUtil.getRenderscriptGenDir(facet), sourceRoot)) {
          modes.add(AndroidAutogeneratorMode.RENDERSCRIPT);
        }
      }
      return Pair.create(module, modes);
    }

    @Override
    public boolean canEat(Update update) {
      if (update instanceof MyUpdate) {
        VirtualFile hisFile = ((MyUpdate)update).myEvent.getFile();
        VirtualFile file = myEvent.getFile();

        if (Comparing.equal(hisFile, file)) {
          return true;
        }

        if (hisFile.getFileType() == AndroidIdlFileType.ourFileType || file.getFileType() == AndroidIdlFileType.ourFileType) {
          return hisFile.getFileType() == file.getFileType();
        }

        if (hisFile.getFileType() == AndroidRenderscriptFileType.INSTANCE || file.getFileType() == AndroidRenderscriptFileType.INSTANCE) {
          return hisFile.getFileType() == file.getFileType();
        }

        return true;
      }
      return false;
    }
  }
}
