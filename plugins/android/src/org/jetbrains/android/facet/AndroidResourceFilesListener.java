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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.*;
import com.intellij.util.containers.HashSet;
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
import org.jetbrains.android.util.ResourceEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.jetbrains.android.util.AndroidUtils.findSourceRoot;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 31, 2009
 * Time: 4:49:30 PM
 * To change this template use File | Settings | File Templates.
 */
class AndroidResourceFilesListener extends VirtualFileAdapter {

  private final MergingUpdateQueue myQueue;
  private final AndroidFacet myFacet;
  private String myCachedPackage = null;

  private volatile Set<ResourceEntry> myResourceSet = new HashSet<ResourceEntry>();
  private static final Object RESOURCES_SET_LOCK = new Object();

  public AndroidResourceFilesListener(final AndroidFacet facet) {
    myFacet = facet;
    myQueue = new MergingUpdateQueue("AndroidResourcesCompilationQueue", 300, true, null, myFacet, null, false);
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        if (facet.getModule().getProject().isDisposed()) {
          return;
        }

        Manifest manifest = facet.getManifest();
        if (manifest != null) {
          myCachedPackage = manifest.getPackage().getValue();
        }
      }
    });
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

  private String getResDirName() {
    return AndroidUtils.getSimpleNameByRelativePath(myFacet.getConfiguration().RES_FOLDER_RELATIVE_PATH);
  }

  private String getManifestFileName() {
    return AndroidUtils.getSimpleNameByRelativePath(myFacet.getConfiguration().MANIFEST_FILE_RELATIVE_PATH);
  }

  private void fileChanged(@NotNull final VirtualFileEvent e) {
    VirtualFile file = e.getFile();

    VirtualFile parent = e.getParent();
    VirtualFile gp = parent != null ? parent.getParent() : null;

    if (file.getFileType() == AndroidIdlFileType.ourFileType ||
        file.getFileType() == AndroidRenderscriptFileType.INSTANCE ||
        getManifestFileName().equals(file.getName()) ||
        (gp != null && gp.isDirectory() && getResDirName().equals(gp.getName()))) {

      myQueue.queue(new MyUpdate(e));
    }
  }

  public void setResourceSet(@NotNull Set<ResourceEntry> resourceSet) {
    synchronized (RESOURCES_SET_LOCK) {
      myResourceSet = resourceSet;
    }
  }

  private class MyUpdate extends Update {
    private final VirtualFileEvent myEvent;

    public MyUpdate(VirtualFileEvent event) {
      super(event.getParent());
      myEvent = event;
    }

    public void run() {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        return;
      }

      final List<AndroidAutogeneratorMode> autogenerationModes =
        ApplicationManager.getApplication().runReadAction(new Computable<List<AndroidAutogeneratorMode>>() {
          @Nullable
          public List<AndroidAutogeneratorMode> compute() {
            return computeCompilersToRunAndInvalidateLocalAttributesMap();
          }
        });

      if (autogenerationModes.isEmpty()) {
        return;
      }

      for (AndroidAutogeneratorMode autogenerationMode : autogenerationModes) {
        if (autogenerationMode == AndroidAutogeneratorMode.AAPT &&
            AndroidRootUtil.getManifestFile(myFacet) != myEvent.getFile()) {

          final HashSet<ResourceEntry> resourceSet = new HashSet<ResourceEntry>();

          DumbService.getInstance(myFacet.getModule().getProject()).waitForSmartMode();

          AndroidCompileUtil.collectAllResources(myFacet, resourceSet);

          synchronized (RESOURCES_SET_LOCK) {
            if (resourceSet.equals(myResourceSet) &&
                !myFacet.areSourcesGeneratedWithErrors(AndroidAutogeneratorMode.AAPT)) {
              return;
            }
            myResourceSet = resourceSet;
          }
        }
        AndroidCompileUtil.generate(myFacet.getModule(), autogenerationMode, true);
      }
    }

    @NotNull
    private List<AndroidAutogeneratorMode> computeCompilersToRunAndInvalidateLocalAttributesMap() {
      if (myFacet.isDisposed()) {
        return Collections.emptyList();
      }
      final Module myModule = myFacet.getModule();
      final Project project = myModule.getProject();

      if (project.isDisposed()) {
        return Collections.emptyList();
      }
      final VirtualFile file = myEvent.getFile();
      final Module module = ModuleUtil.findModuleForFile(file, project);

      if (module != myModule) {
        return Collections.emptyList();
      }

      VirtualFile parent = myEvent.getParent();
      if (parent == null) {
        return Collections.emptyList();
      }

      final VirtualFile gp = parent.getParent();

      final VirtualFile resourceDir = AndroidRootUtil.getResourceDir(myFacet);

      if (gp == resourceDir &&
          ResourceFolderType.VALUES.getName().equals(AndroidCommonUtils.getResourceTypeByDirName(parent.getName()))) {
        myFacet.getLocalResourceManager().invalidateAttributeDefinitions();
      }
      final VirtualFile manifestFile = AndroidRootUtil.getManifestFile(myFacet);

      final List<AndroidAutogeneratorMode> modes = new ArrayList<AndroidAutogeneratorMode>();

      if (AndroidAptCompiler.isToCompileModule(module, myFacet.getConfiguration()) && (gp == resourceDir || manifestFile == file)) {
        final Manifest manifest = myFacet.getManifest();
        final String aPackage = manifest != null ? manifest.getPackage().getValue() : null;

        if (myCachedPackage != null && !myCachedPackage.equals(aPackage)) {
          String aptGenDirPath = AndroidRootUtil.getAptGenSourceRootPath(myFacet);
          AndroidCompileUtil.removeDuplicatingClasses(myModule, myCachedPackage, AndroidUtils.R_CLASS_NAME, null, aptGenDirPath);
        }
        myCachedPackage = aPackage;
        modes.add(AndroidAutogeneratorMode.AAPT);
      }

      if (file.getFileType() == AndroidIdlFileType.ourFileType) {
        VirtualFile sourceRoot = findSourceRoot(myModule, file);
        if (sourceRoot != null && AndroidRootUtil.getAidlGenDir(myFacet) != sourceRoot) {
          modes.add(AndroidAutogeneratorMode.AIDL);
        }
      }

      if (file.getFileType() == AndroidRenderscriptFileType.INSTANCE) {
        final VirtualFile sourceRoot = findSourceRoot(myModule, file);
        if (sourceRoot != null && AndroidRootUtil.getRenderscriptGenDir(myFacet) != sourceRoot) {
          modes.add(AndroidAutogeneratorMode.RENDERSCRIPT);
        }
      }

      if (manifestFile == file) {
        modes.add(AndroidAutogeneratorMode.BUILDCONFIG);
      }
      return modes;
    }

    @Override
    public boolean canEat(Update update) {
      if (update instanceof MyUpdate) {
        VirtualFile hisFile = ((MyUpdate)update).myEvent.getFile();
        VirtualFile file = myEvent.getFile();
        
        if (hisFile == file) {
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
