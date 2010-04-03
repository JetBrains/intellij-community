/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.options.StreamProvider;
import com.intellij.util.io.fs.IFile;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

//todo: extends from base store class
public class DefaultProjectStoreImpl extends ProjectStoreImpl {
  @Nullable private final Element myElement;
  private final ProjectManagerImpl myProjectManager;
  @NonNls private static final String ROOT_TAG_NAME = "defaultProject";

  public DefaultProjectStoreImpl(final ProjectImpl project, final ProjectManagerImpl projectManager) {
    super(project);
    myProjectManager = projectManager;

    myElement = projectManager.getDefaultProjectRootElement();
  }

  @Nullable
  Element getStateCopy() {
    final Element element = myProjectManager.getDefaultProjectRootElement();
    return element != null ? (Element)element.clone() : null;
  }


  @Override
  protected StateStorageManager createStateStorageManager() {
    Document _d = null;

    if (myElement != null) {
      myElement.detach();
      _d = new Document(myElement);
    }

    final ComponentManager componentManager = getComponentManager();
    final PathMacroManager pathMacroManager = PathMacroManager.getInstance(componentManager);

    final Document document = _d;

    final XmlElementStorage storage = new XmlElementStorage(pathMacroManager.createTrackingSubstitutor(), componentManager,
                                                            ROOT_TAG_NAME, StreamProvider.DEFAULT, "", ComponentRoamingManager.getInstance(),
                                                            ComponentVersionProvider.EMPTY) {
      @Nullable
      protected Document loadDocument() throws StateStorageException {
        return document;
      }

      protected MySaveSession createSaveSession(final MyExternalizationSession externalizationSession) {
        return new DefaultSaveSession(externalizationSession);
      }

      @NotNull
      protected StorageData createStorageData() {
        return new BaseStorageData(ROOT_TAG_NAME);
      }

      class DefaultSaveSession extends MySaveSession {
        public DefaultSaveSession(MyExternalizationSession externalizationSession) {
          super(externalizationSession);
        }

        protected void doSave() throws StateStorageException {
          myProjectManager.setDefaultProjectRootElement(getDocumentToSave().getRootElement());
        }

        public Collection<IFile> getStorageFilesToSave() throws StateStorageException {
          return Collections.emptyList();
        }

        public List<IFile> getAllStorageFiles() {
          return Collections.emptyList();
        }
      }
    };

    return new StateStorageManager() {
      public void addMacro(String macro, String expansion) {
        throw new UnsupportedOperationException("Method addMacro not implemented in " + getClass());
      }

      @Nullable
      public TrackingPathMacroSubstitutor getMacroSubstitutor() {
        return null;
      }

      @Nullable
      public StateStorage getStateStorage(@NotNull Storage storageSpec) throws StateStorage.StateStorageException {
        return storage;
      }

      @Nullable
      public StateStorage getFileStateStorage(String fileName) {
        return storage;
      }

      public void clearStateStorage(@NotNull String file) {
      }

      public ExternalizationSession startExternalization() {
        return new MyExternalizationSession(storage);
      }

      public SaveSession startSave(final ExternalizationSession externalizationSession) {
        return new MySaveSession(storage, externalizationSession);
      }

      public void finishSave(SaveSession saveSession) {
        storage.finishSave(((MySaveSession)saveSession).saveSession);
      }

      public String expandMacroses(final String file) {
        throw new UnsupportedOperationException("Method expandMacroses not implemented in " + getClass());
      }

      @Nullable
      public StateStorage getOldStorage(Object component, final String componentName, final StateStorageOperation operation)
      throws StateStorage.StateStorageException {
        return storage;
      }

      public void registerStreamProvider(final StreamProvider streamProvider, final RoamingType type) {
        throw new UnsupportedOperationException("Method registerStreamProvider not implemented in " + getClass());
      }

      public void unregisterStreamProvider(final StreamProvider streamProvider, final RoamingType roamingType) {
        throw new UnsupportedOperationException("Method unregisterStreamProvider not implemented in " + getClass());
      }
      public StreamProvider[] getStreamProviders(final RoamingType roamingType) {
        throw new UnsupportedOperationException("Method getStreamProviders not implemented in " + getClass());
      }

      public Collection<String> getStorageFileNames() {
        throw new UnsupportedOperationException("Method getStorageFileNames not implemented in " + getClass());
      }

      public void reset() {
        
      }
    };
  }

  public String getLocation() {
    throw new UnsupportedOperationException("Method getLocation not implemented in " + getClass());
  }

  public void load() throws IOException, StateStorage.StateStorageException {
    if (myElement == null) return;
    super.load();
  }

  private static class MyExternalizationSession implements StateStorageManager.ExternalizationSession {
    StateStorage.ExternalizationSession externalizationSession;

    public MyExternalizationSession(final XmlElementStorage storage) {
      externalizationSession = storage.startExternalization();
    }

    public void setState(@NotNull final Storage[] storageSpecs, final Object component, final String componentName, final Object state)
    throws StateStorage.StateStorageException {
      externalizationSession.setState(component, componentName, state, null);
    }

    public void setStateInOldStorage(final Object component, final String componentName, final Object state) throws StateStorage.StateStorageException {
      externalizationSession.setState(component, componentName, state, null);
    }
  }

  private static class MySaveSession implements StateStorageManager.SaveSession {
    StateStorage.SaveSession saveSession;

    public MySaveSession(final XmlElementStorage storage, final StateStorageManager.ExternalizationSession externalizationSession) {
      saveSession = storage.startSave(((MyExternalizationSession)externalizationSession).externalizationSession);
    }

    //returns set of component which were changed, null if changes are much more than just component state.
    @Nullable
    public Set<String> analyzeExternalChanges(Set<Pair<VirtualFile, StateStorage>> files) {
      throw new UnsupportedOperationException("Method analyzeExternalChanges not implemented in " + getClass());
    }

    public List<IFile> getAllStorageFilesToSave() throws StateStorage.StateStorageException {
      return Collections.emptyList();
    }

    public List<IFile> getAllStorageFiles() {
      return Collections.emptyList();
    }

    public void save() throws StateStorage.StateStorageException {
      saveSession.save();
    }
  }
}
