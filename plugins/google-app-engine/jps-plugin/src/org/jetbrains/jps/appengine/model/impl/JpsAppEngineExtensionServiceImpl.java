/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.jps.appengine.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.appengine.model.JpsAppEngineExtensionService;
import org.jetbrains.jps.appengine.model.JpsAppEngineModuleExtension;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author nik
 */
public class JpsAppEngineExtensionServiceImpl extends JpsAppEngineExtensionService {
  @Nullable
  @Override
  public JpsAppEngineModuleExtension getExtension(@NotNull JpsModule module) {
    return module.getContainer().getChild(JpsAppEngineModuleExtensionImpl.ROLE);
  }
}
