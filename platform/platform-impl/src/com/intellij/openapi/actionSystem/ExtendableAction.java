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
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Experimental
public class ExtendableAction extends AnAction {
  @NotNull private final ExtensionPointName<AnActionExtensionProvider> myExtensionPoint;
  @NotNull private final DataKey<AnActionExtensionProvider> myDataKey;

  public ExtendableAction(@NotNull ExtensionPointName<AnActionExtensionProvider> extensionPoint) {
    myExtensionPoint = extensionPoint;
    myDataKey = getDataKey(extensionPoint);
  }

  @NotNull
  protected static DataKey<AnActionExtensionProvider> getDataKey(@NotNull ExtensionPointName<AnActionExtensionProvider> extensionPoint) {
    return DataKey.create(extensionPoint.getName());
  }

  @Override
  public final void update(@NotNull AnActionEvent e) {
    e.getPresentation().copyFrom(getTemplatePresentation());

    AnActionExtensionProvider provider = getProvider(e);
    if (provider != null) {
      provider.update(e);
    }
    else {
      defaultUpdate(e);
    }
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    AnActionExtensionProvider provider = getProvider(e);
    if (provider != null) {
      provider.actionPerformed(e);
    }
    else {
      defaultActionPerformed(e);
    }
  }

  @Nullable
  private AnActionExtensionProvider getProvider(@NotNull AnActionEvent e) {
    List<AnActionExtensionProvider> localProviders = ContainerUtil.packNullables(e.getData(myDataKey));
    List<AnActionExtensionProvider> globalProviders = ContainerUtil.list(myExtensionPoint.getExtensions());
    List<AnActionExtensionProvider> providers = ContainerUtil.concat(localProviders, globalProviders);
    return ContainerUtil.find(providers, provider -> provider.isActive(e));
  }

  protected void defaultUpdate(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(false);
  }

  protected void defaultActionPerformed(@NotNull AnActionEvent e) {
  }
}
