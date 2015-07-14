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

import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.PathMacroSubstitutor;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.util.SmartList;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class BaseFileConfigurableStoreImpl extends ComponentStoreImpl {
  @NonNls protected static final String VERSION_OPTION = "version";
  @NonNls public static final String ATTRIBUTE_NAME = "name";

  private static final List<String> ourConversionProblemsStorage = new SmartList<String>();

  private StateStorageManager myStateStorageManager;
  protected final PathMacroManager myPathMacroManager;

  protected BaseFileConfigurableStoreImpl(@NotNull PathMacroManager pathMacroManager) {
    myPathMacroManager = pathMacroManager;
  }

  protected static class BaseStorageData extends StorageData {
    private int myVersion = ProjectManagerImpl.CURRENT_FORMAT_VERSION;

    public BaseStorageData(@NotNull String rootElementName) {
      super(rootElementName);
    }

    protected BaseStorageData(BaseStorageData storageData) {
      super(storageData);
    }

    @Override
    public void load(@NotNull Element rootElement, @Nullable PathMacroSubstitutor pathMacroSubstitutor, boolean intern) {
      super.load(rootElement, pathMacroSubstitutor, intern);

      String v = rootElement.getAttributeValue(VERSION_OPTION);
      myVersion = v == null ? ProjectManagerImpl.CURRENT_FORMAT_VERSION : Integer.parseInt(v);
    }

    @Override
    @NotNull
    protected final Element save(@NotNull Map<String, Element> newLiveStates) {
      Element root = super.save(newLiveStates);
      if (root == null) {
        root = new Element(myRootElementName);
      }
      writeOptions(root, Integer.toString(myVersion));
      return root;
    }

    protected void writeOptions(@NotNull Element root, @NotNull String versionString) {
      root.setAttribute(VERSION_OPTION, versionString);
    }

    @Override
    public StorageData clone() {
      return new BaseStorageData(this);
    }

    @Nullable
    @Override
    public Set<String> getChangedComponentNames(@NotNull StorageData newStorageData, @Nullable PathMacroSubstitutor substitutor) {
      BaseStorageData data = (BaseStorageData)newStorageData;
      if (myVersion != data.myVersion) {
        return null;
      }
      return super.getChangedComponentNames(newStorageData, substitutor);
    }
  }

  @NotNull
  protected abstract XmlElementStorage getMainStorage();

  @Nullable
  static List<String> getConversionProblemsStorage() {
    return ourConversionProblemsStorage;
  }

  public BaseStorageData getMainStorageData() {
    return (BaseStorageData)getMainStorage().getStorageData();
  }

  @NotNull
  @Override
  protected final PathMacroManager getPathMacroManagerForDefaults() {
    return myPathMacroManager;
  }

  @NotNull
  @Override
  public final StateStorageManager getStateStorageManager() {
    if (myStateStorageManager == null) {
      myStateStorageManager = createStateStorageManager();
    }
    return myStateStorageManager;
  }

  @NotNull
  protected abstract StateStorageManager createStateStorageManager();
}
