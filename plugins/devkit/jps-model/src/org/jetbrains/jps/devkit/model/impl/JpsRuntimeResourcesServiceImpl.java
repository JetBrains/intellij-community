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
import org.jetbrains.jps.devkit.model.JpsRuntimeResourcesService;
import org.jetbrains.jps.model.JpsElementCollection;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.ex.JpsElementCollectionRole;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author nik
 */
public class JpsRuntimeResourcesServiceImpl extends JpsRuntimeResourcesService {
  private static final JpsElementCollectionRole<JpsRuntimeResourceRoot> ROLE = JpsElementCollectionRole.create(JpsElementChildRoleBase.<JpsRuntimeResourceRoot>create("runtime-resources"));

  @Nullable
  @Override
  public JpsRuntimeResourceRootsCollection getRoots(@NotNull JpsModule module) {
    JpsElementCollection<JpsRuntimeResourceRoot> child = module.getContainer().getChild(ROLE);
    return child != null ? new JpsRuntimeResourceRootsCollectionImpl(child) : null;
  }

  @NotNull
  @Override
  public JpsRuntimeResourceRootsCollection getOrCreateRoots(@NotNull JpsModule module) {
    return new JpsRuntimeResourceRootsCollectionImpl(module.getContainer().getOrSetChild(ROLE));
  }
}
