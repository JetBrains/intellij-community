// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.roots;

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
public abstract class ModuleExtension {
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
  public abstract @NotNull ModuleExtension getModifiableModel(final boolean writable);

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