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
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.openapi.util.registry.RegistryValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class RegistryToggleAction extends ToggleAction {

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
  public final boolean isSelected(@NotNull AnActionEvent e) {
    return myRegistryValue.asBoolean();
  }

  @Override
  public final @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public final void setSelected(@NotNull AnActionEvent e, boolean state) {
    myRegistryValue.setValue(state);
  }
}
