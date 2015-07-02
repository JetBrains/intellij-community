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
package org.jetbrains.jps.devkit.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.devkit.model.JpsRuntimeResourceRoot;
import org.jetbrains.jps.devkit.model.JpsRuntimeResourceRootsCollection;
import org.jetbrains.jps.model.JpsElementCollection;

import java.util.List;

/**
 * @author nik
 */
public class JpsRuntimeResourceRootsCollectionImpl implements JpsRuntimeResourceRootsCollection {
  private final JpsElementCollection<JpsRuntimeResourceRoot> myCollection;

  public JpsRuntimeResourceRootsCollectionImpl(JpsElementCollection<JpsRuntimeResourceRoot> collection) {
    myCollection = collection;
  }

  @Override
  public void addRoot(@NotNull String name, @NotNull String url) {
    myCollection.addChild(new JpsRuntimeResourceRootImpl(name, url));
  }

  @Override
  public void removeRoot(@NotNull JpsRuntimeResourceRoot root) {
    myCollection.removeChild(root);
  }

  @Override
  public void removeAllRoots() {
    myCollection.removeAllChildren();
  }

  @NotNull
  @Override
  public List<JpsRuntimeResourceRoot> getRoots() {
    return myCollection.getElements();
  }

  @Nullable
  @Override
  public JpsRuntimeResourceRoot findRoot(@NotNull String name) {
    for (JpsRuntimeResourceRoot root : getRoots()) {
      if (name.equals(root.getName())) {
        return root;
      }
    }
    return null;
  }
}
