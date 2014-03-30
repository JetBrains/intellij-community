/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

abstract class BaseFileConfigurableStoreImpl extends ComponentStoreImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.BaseFileConfigurableStoreImpl");

  @NonNls protected static final String VERSION_OPTION = "version";
  @NonNls public static final String ATTRIBUTE_NAME = "name";
  private final ComponentManager myComponentManager;
  private static final ArrayList<String> ourConversionProblemsStorage = new ArrayList<String>();
  private final DefaultsStateStorage myDefaultsStateStorage;
  private StateStorageManager myStateStorageManager;


  protected BaseFileConfigurableStoreImpl(final ComponentManager componentManager) {
    myComponentManager = componentManager;
    final PathMacroManager pathMacroManager = PathMacroManager.getInstance(myComponentManager);
    myDefaultsStateStorage = new DefaultsStateStorage(pathMacroManager);
  }

  public synchronized ComponentManager getComponentManager() {
    return myComponentManager;
  }

  protected static class BaseStorageData extends FileBasedStorage.FileStorageData {
    protected int myVersion;

    public BaseStorageData(final String rootElementName) {
      super(rootElementName);
    }

    protected BaseStorageData(BaseStorageData storageData) {
      super(storageData);

      myVersion = ProjectManagerImpl.CURRENT_FORMAT_VERSION;
    }

    @Override
    public void load(@NotNull final Element rootElement) throws IOException {
      super.load(rootElement);

      final String v = rootElement.getAttributeValue(VERSION_OPTION);
      if (v != null) {
        myVersion = Integer.parseInt(v);
      }
      else {
        myVersion = ProjectManagerImpl.CURRENT_FORMAT_VERSION;
      }
    }

    @Override
    @NotNull
    protected Element save() {
      final Element root = super.save();
      root.setAttribute(VERSION_OPTION, Integer.toString(myVersion));
      return root;
    }

    @Override
    public StorageData clone() {
      return new BaseStorageData(this);
    }

    @Override
    protected int computeHash() {
      int result = super.computeHash();
      result = result * 31 + myVersion;
      return result;
    }

    @Override
    @Nullable
    public Set<String> getDifference(final StorageData storageData, PathMacroSubstitutor substitutor) {
      final BaseStorageData data = (BaseStorageData)storageData;
      if (myVersion != data.myVersion) return null;
      return super.getDifference(storageData, substitutor);
    }
  }

  protected abstract XmlElementStorage getMainStorage();

  @Nullable
  static ArrayList<String> getConversionProblemsStorage() {
    return ourConversionProblemsStorage;
  }

  @Override
  public void load() throws IOException, StateStorageException {
    getMainStorageData(); //load it
  }

  public BaseStorageData getMainStorageData() throws StateStorageException {
    return (BaseStorageData) getMainStorage().getStorageData(false);
  }

  @Override
  protected StateStorage getDefaultsStorage() {
    return myDefaultsStateStorage;
  }

  @NotNull
  @Override
  public StateStorageManager getStateStorageManager() {
    if (myStateStorageManager == null) {
      myStateStorageManager = createStateStorageManager();
    }
    return myStateStorageManager;
  }

  @NotNull
  protected abstract StateStorageManager createStateStorageManager();
}
