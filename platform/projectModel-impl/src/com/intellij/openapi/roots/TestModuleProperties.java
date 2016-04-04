/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * In some cases tests need to be extracted to a separate module (because they have a different classpath, output folder or JDK). E.g. when
 * the project is imported from Gradle IDEA creates separate modules for each source set of a Gradle project.
 * <p/>
 * This service allows to specify to which production module the tests module belongs. This information may be used for example by
 * 'Create Test' feature.
 * <p/>
 * <strong>This API isn't stable for now and may be changed in future. Also it isn't possible to change this in UI.</strong>
 * @author nik
 */
public abstract class TestModuleProperties {
  public static TestModuleProperties getInstance(@NotNull Module module) {
    return ModuleServiceManager.getService(module, TestModuleProperties.class);
  }

  @Nullable
  public abstract String getProductionModuleName();

  @Nullable
  public abstract Module getProductionModule();

  public abstract void setProductionModuleName(@Nullable String moduleName);
}
