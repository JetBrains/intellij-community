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
package org.jetbrains.idea.devkit.module;

import com.intellij.openapi.roots.ModuleExtension;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.devkit.model.impl.JpsDevKitModelSerializerExtension;
import org.jetbrains.jps.devkit.model.impl.RuntimeResourceRootState;

import java.util.*;

/**
 * @author nik
 */
public class RuntimeResourcesConfigurationImpl extends RuntimeResourcesConfiguration {
  private final RuntimeResourcesConfigurationImpl mySource;
  private Map<String, RuntimeResourceRoot> myRoots = new LinkedHashMap<String, RuntimeResourceRoot>();
  private boolean myLoadedByExtension;

  public RuntimeResourcesConfigurationImpl() {
    mySource = null;
  }

  public RuntimeResourcesConfigurationImpl(RuntimeResourcesConfigurationImpl source) {
    mySource = source;
    copyRootsFrom(source);
    myLoadedByExtension = source.myLoadedByExtension;
  }

  @Override
  public ModuleExtension getModifiableModel(boolean writable) {
    return new RuntimeResourcesConfigurationImpl(this);
  }

  @Override
  public void commit() {
    mySource.copyRootsFrom(this);
  }

  @Override
  public boolean isChanged() {
    if (!myRoots.keySet().equals(mySource.myRoots.keySet())) return true;

    for (RuntimeResourceRoot root : myRoots.values()) {
      RuntimeResourceRoot other = mySource.myRoots.get(root.getName());
      if (!other.getUrl().equals(root.getUrl())) return true;
    }
    return false;
  }

  private void copyRootsFrom(RuntimeResourcesConfigurationImpl source) {
    myRoots.clear();
    VirtualFilePointerManager pointerManager = VirtualFilePointerManager.getInstance();
    for (RuntimeResourceRoot root : source.myRoots.values()) {
      myRoots.put(root.getName(), new RuntimeResourceRoot(root.getName(), pointerManager.duplicate(root.getFilePointer(), this, null)));
    }
  }

  @Override
  @NotNull
  public Collection<RuntimeResourceRoot> getRoots() {
    return Collections.unmodifiableCollection(myRoots.values());
  }

  @Nullable
  @Override
  public RuntimeResourceRoot getRoot(@NotNull String name) {
    return myRoots.get(name);
  }

  @Override
  public void setRoots(@NotNull List<RuntimeResourceRoot> roots) {
    myRoots.clear();
    for (RuntimeResourceRoot root : roots) {
      myRoots.put(root.getName(), root);
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    if (myLoadedByExtension) {
      JpsDevKitModelSerializerExtension.RuntimeResourceListState state = getState();
      XmlSerializer.serializeInto(state, element);
    }
  }

  @NotNull
  JpsDevKitModelSerializerExtension.RuntimeResourceListState getState() {
    JpsDevKitModelSerializerExtension.RuntimeResourceListState state = new JpsDevKitModelSerializerExtension.RuntimeResourceListState();
    for (RuntimeResourceRoot root : myRoots.values()) {
      state.myRoots.add(new RuntimeResourceRootState(root.getName(), root.getUrl()));
    }
    return state;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    if (!myLoadedByExtension && !myRoots.isEmpty()) {
      return;
    }
    JpsDevKitModelSerializerExtension.RuntimeResourceListState state = new JpsDevKitModelSerializerExtension.RuntimeResourceListState();
    XmlSerializer.deserializeInto(state, element);
    loadState(state);
    myLoadedByExtension = !myRoots.isEmpty();
  }

  boolean isLoadedByExtension() {
    return myLoadedByExtension;
  }

  void loadState(@Nullable JpsDevKitModelSerializerExtension.RuntimeResourceListState state) {
    myRoots.clear();
    if (state != null) {
      VirtualFilePointerManager pointerManager = VirtualFilePointerManager.getInstance();
      for (RuntimeResourceRootState root : state.myRoots) {
        myRoots.put(root.myName, new RuntimeResourceRoot(root.myName, pointerManager.create(root.myUrl, this, null)));
      }
    }
  }

  @Override
  public void dispose() {
  }
}
