/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.options.StreamProvider;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.util.Couple;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

//todo: extends from base store class
public class DefaultProjectStoreImpl extends ProjectStoreImpl {
  @Nullable private final Element myElement;
  private final ProjectManagerImpl myProjectManager;
  @NonNls private static final String ROOT_TAG_NAME = "defaultProject";

  public DefaultProjectStoreImpl(@NotNull ProjectImpl project, @NotNull ProjectManagerImpl projectManager) {
    super(project);

    myProjectManager = projectManager;
    myElement = projectManager.getDefaultProjectRootElement();
  }

  @Nullable
  Element getStateCopy() {
    final Element element = myProjectManager.getDefaultProjectRootElement();
    return element != null ? element.clone() : null;
  }

  @NotNull
  @Override
  protected StateStorageManager createStateStorageManager() {
    Element _d = null;

    if (myElement != null) {
      myElement.detach();
      _d = myElement;
    }

    ComponentManager componentManager = getComponentManager();
    final Element element = _d;
    final XmlElementStorage storage = new XmlElementStorage("", RoamingType.DISABLED, PathMacroManager.getInstance(componentManager).createTrackingSubstitutor(), componentManager,
                                                            ROOT_TAG_NAME, null,
                                                            ComponentVersionProvider.EMPTY) {
      @Override
      @Nullable
      protected Element loadLocalData() {
        return element;
      }

      @Override
      protected MySaveSession createSaveSession(@NotNull StorageData storageData) {
        return new MySaveSession(storageData) {
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
      public StateStorage getStateStorage(@NotNull Storage storageSpec) throws StateStorageException {
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
      @Nullable
      public StateStorage getFileStateStorage(@NotNull String fileSpec) {
        return storage;
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

      @Nullable
      @Override
      public SaveSession startSave(@NotNull ExternalizationSession externalizationSession) {
        return storage.startSave(((MyExternalizationSession)externalizationSession).externalizationSession);
      }

      @Override
      public void finishSave(@NotNull SaveSession saveSession) {
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
      public void registerStreamProvider(final StreamProvider streamProvider, final RoamingType type) {
        throw new UnsupportedOperationException("Method registerStreamProvider not implemented in " + getClass());
      }

      @Override
      public void setStreamProvider(@Nullable com.intellij.openapi.components.impl.stores.StreamProvider streamProvider) {
        throw new UnsupportedOperationException("Method setStreamProvider not implemented in " + getClass());
      }

      @Nullable
      @Override
      public com.intellij.openapi.components.impl.stores.StreamProvider getStreamProvider() {
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
  public void load() throws IOException, StateStorageException {
    if (myElement == null) return;
    super.load();
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
  }
}
