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
package com.intellij.project.model.impl.module;

import com.intellij.openapi.module.Module;
import com.intellij.project.model.JpsModuleManager;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class JpsModuleManagerImpl implements JpsModuleManager {
  private final Map<JpsModule, Module> myModules = new HashMap<>();

  @Override
  public Module getModule(JpsModule jpsModule) {
    return myModules.get(jpsModule);
  }
}
