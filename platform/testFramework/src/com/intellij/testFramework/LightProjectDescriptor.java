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
package com.intellij.testFramework;

import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;

/**
 * @author yole
 */
public interface LightProjectDescriptor {
  ModuleType getModuleType();
  Sdk getSdk();
  void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry);

  class Empty implements LightProjectDescriptor {
    @Override
    public ModuleType getModuleType() {
      return EmptyModuleType.getInstance();
    }

    @Override
    public Sdk getSdk() {
      return null;
    }

    @Override
    public void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
    }
  }

  LightProjectDescriptor EMPTY_PROJECT_DESCRIPTOR = new Empty();
}
