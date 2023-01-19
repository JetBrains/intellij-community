/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Extend this class to provide additional module-level properties which can be edited in Project Structure dialog. For ordinary module-level
 * properties use {@link com.intellij.openapi.module.ModuleServiceManager module service} instead.
 * <p/>
 * If the inheritor implements {@link com.intellij.openapi.components.PersistentStateComponent} its state will be persisted in the module
 * configuration file.
 */
public abstract class ModuleExtension implements Disposable {
  /**
   * <b>Note:</b> don't call this method directly from client code. Use approach below instead:
   * <pre>
   *   ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
   *   CompilerModuleExtension extension = modifiableModel.getModuleExtension(CompilerModuleExtension.class);
   *   try {
   *     // ...
   *   }
   *   finally {
   *     modifiableModel.commit();
   *   }
   * </pre>
   * The point is that call to commit() on CompilerModuleExtension obtained like
   * {@code 'CompilerModuleExtension.getInstance(module).getModifiableModel(true)'} doesn't dispose the model.
   * <p/>
   * Call to {@code ModifiableRootModel.commit()} not only commits linked extensions but disposes them as well.
   * 
   * @param writable  flag which identifies if resulting model is writable
   * @return          extension model
   */
  @ApiStatus.OverrideOnly
  @NotNull
  public abstract ModuleExtension getModifiableModel(final boolean writable);

  public abstract void commit();

  public abstract boolean isChanged();

  /**
   * @deprecated Please implement PersistentStateComponent instead.
   */
  @Deprecated(forRemoval = true)
  public void readExternal(@NotNull Element element) {
    throw new UnsupportedOperationException("Implement PersistentStateComponent");
  }

  /**
   * @deprecated Please implement PersistentStateComponent instead.
   */
  @Deprecated(forRemoval = true)
  public void writeExternal(@NotNull Element element) {
    throw new UnsupportedOperationException("Implement PersistentStateComponent");
  }
}