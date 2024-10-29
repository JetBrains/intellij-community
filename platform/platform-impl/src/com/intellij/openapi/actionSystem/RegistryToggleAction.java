// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.openapi.util.registry.RegistryValue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class RegistryToggleAction extends ToggleAction {

  private final @NotNull RegistryValue myRegistryValue;

  public RegistryToggleAction(@NotNull RegistryValue registryValue) {
    this(registryValue, null);
  }

  public RegistryToggleAction(@NotNull @PropertyKey(resourceBundle = Registry.REGISTRY_BUNDLE) String key) {
    this(key, null);
  }

  public RegistryToggleAction(@NotNull RegistryValue registryValue,
                              @Nullable @NlsActions.ActionText String name) {
    this(registryValue, name, null, null);
  }

  public RegistryToggleAction(@NotNull @PropertyKey(resourceBundle = Registry.REGISTRY_BUNDLE) String key,
                              @Nullable @NlsActions.ActionText String name) {
    this(key, name, null, null);
  }

  public RegistryToggleAction(@NotNull RegistryValue registryValue,
                              @Nullable @NlsActions.ActionText String name,
                              @Nullable @NlsActions.ActionDescription String description,
                              @Nullable Icon icon) {
    super(name, description, icon);
    myRegistryValue = registryValue;
  }

  public RegistryToggleAction(@NotNull @PropertyKey(resourceBundle = Registry.REGISTRY_BUNDLE) String key,
                              @Nullable @NlsActions.ActionText String name,
                              @Nullable @NlsActions.ActionDescription String description,
                              @Nullable Icon icon) {
    this(RegistryManager.getInstance().get(key),
         name,
         description,
         icon);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return myRegistryValue.asBoolean();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    myRegistryValue.setValue(state);
  }
}
