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

import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class RegistryToggleAction extends ToggleAction {
  @NotNull
  private final String myKey;

  public RegistryToggleAction(@NotNull @PropertyKey(resourceBundle = Registry.REGISTRY_BUNDLE) String key) {
    this(key, null, null, null);
  }

  public RegistryToggleAction(@NotNull @PropertyKey(resourceBundle = Registry.REGISTRY_BUNDLE) String key,
                              @Nullable String name) {
    this(key, name, null, null);
  }

  public RegistryToggleAction(@NotNull @PropertyKey(resourceBundle = Registry.REGISTRY_BUNDLE) String key,
                              @Nullable String name,
                              @Nullable String description,
                              @Nullable Icon icon) {
    super(name, description, icon);
    myKey = key;
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return Registry.is(myKey);
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    Registry.get(myKey).setValue(state);
    doWhenDone(e);
  }

  public void doWhenDone(AnActionEvent e) {
  }
}
