/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.*;
import com.intellij.openapi.components.StateStorage.SaveSession;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.util.Couple;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DefaultProjectStoreImpl extends ProjectStoreImpl {
  private final ProjectManagerImpl myProjectManager;
  @NonNls private static final String ROOT_TAG_NAME = "defaultProject";

  public DefaultProjectStoreImpl(@NotNull ProjectImpl project, @NotNull ProjectManagerImpl projectManager, @NotNull PathMacroManager pathMacroManager) {
    super(project, pathMacroManager);

    myProjectManager = projectManager;
  }

  @Nullable
  Element getStateCopy() {
    final Element element = myProjectManager.getDefaultProjectRootElement();
    return element != null ? element.clone() : null;
  }

  @NotNull
  @Override
  protected StateStorageManager createStateStorageManager() {
    final XmlElementStorage storage = new XmlElementStorage("", RoamingType.DISABLED, myPathMacroManager.createTrackingSubstitutor(),
                                                            ROOT_TAG_NAME, null) {
      @Override
      @Nullable
      protected Element loadLocalData() {
        return getStateCopy();
      }

      @NotNull
      @Override
      protected XmlElementStorageSaveSession createSaveSession(@NotNull StorageData storageData) {
        return new XmlElementStorageSaveSession(storageData) {
          @Override
          protected void doSave(@Nullable Element element) {
            // we must set empty element instead of null as indicator - ProjectManager state is ready to save
            myProjectManager.setDefaultProjectRootElement(element == null ? new Element("empty") : element);
          }

          // we must not collapse paths here, because our solution is just a big hack
          // by default, getElementToSave() returns collapsed paths -> setDefaultProjectRootElement -> project manager writeExternal -> save -> compare old and new - diff because old has expanded, but new collapsed
          // -> needless save
          @Override
          protected boolean isCollapsePathsOnSave() {
            return false;
          }
        };
      }

      @Override
      @NotNull
      protected StorageData createStorageData() {
        return new BaseStorageData(ROOT_TAG_NAME);
      }
    };

    //noinspection deprecation
    return new StateStorageManager() {
      @Override
      public void addMacro(@NotNull String macro, @NotNull String expansion) {
        throw new UnsupportedOperationException("Method addMacro not implemented in " + getClass());
      }

      @Override
      @Nullable
      public TrackingPathMacroSubstitutor getMacroSubstitutor() {
        return null;
      }

      @Override
      @Nullable
      public StateStorage getStateStorage(@NotNull Storage storageSpec) {
        return storage;
      }

      @Nullable
      @Override
      public StateStorage getStateStorage(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
        return storage;
      }

      @NotNull
      @Override
      public Couple<Collection<FileBasedStorage>> getCachedFileStateStorages(@NotNull Collection<String> changed, @NotNull Collection<String> deleted) {
        return new Couple<Collection<FileBasedStorage>>(Collections.<FileBasedStorage>emptyList(), Collections.<FileBasedStorage>emptyList());
      }

      @Override
      public void clearStateStorage(@NotNull String file) {
      }

      @Nullable
      @Override
      public ExternalizationSession startExternalization() {
        StateStorage.ExternalizationSession externalizationSession = storage.startExternalization();
        return externalizationSession == null ? null : new MyExternalizationSession(externalizationSession);
      }

      @NotNull
      @Override
      public String expandMacros(@NotNull String file) {
        throw new UnsupportedOperationException("Method expandMacros not implemented in " + getClass());
      }

      @NotNull
      @Override
      public String collapseMacros(@NotNull String path) {
        throw new UnsupportedOperationException("Method collapseMacros not implemented in " + getClass());
      }

      @Override
      @Nullable
      public StateStorage getOldStorage(@NotNull Object component, @NotNull String componentName, @NotNull StateStorageOperation operation) {
        return storage;
      }

      @Override
      public void setStreamProvider(@Nullable StreamProvider streamProvider) {
        throw new UnsupportedOperationException("Method setStreamProvider not implemented in " + getClass());
      }

      @Nullable
      @Override
      public StreamProvider getStreamProvider() {
        throw new UnsupportedOperationException("Method getStreamProviders not implemented in " + getClass());
      }

      @NotNull
      @Override
      public Collection<String> getStorageFileNames() {
        throw new UnsupportedOperationException("Method getStorageFileNames not implemented in " + getClass());
      }
    };
  }

  @Override
  public void load() {
    if (myProjectManager.getDefaultProjectRootElement() != null) {
      super.load();
    }
  }

  private static class MyExternalizationSession implements StateStorageManager.ExternalizationSession {
    @NotNull final StateStorage.ExternalizationSession externalizationSession;

    public MyExternalizationSession(@NotNull StateStorage.ExternalizationSession externalizationSession) {
      this.externalizationSession = externalizationSession;
    }

    @Override
    public void setState(@NotNull Storage[] storageSpecs, @NotNull Object component, @NotNull String componentName, @NotNull Object state) {
      externalizationSession.setState(component, componentName, state, null);
    }

    @Override
    public void setStateInOldStorage(@NotNull Object component, @NotNull String componentName, @NotNull Object state) {
      externalizationSession.setState(component, componentName, state, null);
    }

    @NotNull
    @Override
    public List<SaveSession> createSaveSessions() {
      return ContainerUtil.createMaybeSingletonList(externalizationSession.createSaveSession());
    }
  }
}
