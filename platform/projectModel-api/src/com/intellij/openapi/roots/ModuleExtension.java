// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
  public abstract @NotNull ModuleExtension getModifiableModel(final boolean writable);

  public abstract void commit();

  /**
   * @deprecated necessity to dispose all instances of {@link ModuleExtension} leads to performance problems in big projects, so this method
   * will be removed in the future.
   * If the implementation just clears the fields by assigning {@code null}, it can be safely removed, because obsolete instances will be 
   * collected by GC anyway.
   * If the implementation really needs to register something in {@link com.intellij.openapi.util.Disposer}, it should create a separate
   * project-level service, implement {@link Disposable} in it and use it as a parent disposable for such resources. 
   */
  @Deprecated(forRemoval = true)
  @Override
  public void dispose() {
  }

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