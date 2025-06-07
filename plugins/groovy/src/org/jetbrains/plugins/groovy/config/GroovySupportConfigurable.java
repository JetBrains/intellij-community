// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.config;

import com.intellij.framework.addSupport.FrameworkSupportInModuleConfigurable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class GroovySupportConfigurable extends FrameworkSupportInModuleConfigurable {
  @Override
  public JComponent createComponent() {
    return null;
  }

  @Override
  public @NotNull CustomLibraryDescription createLibraryDescription() {
    return new GroovyLibraryDescription();
  }

  @Override
  public boolean isOnlyLibraryAdded() {
    return true;
  }

  @Override
  public void addSupport(@NotNull Module module,
                         @NotNull ModifiableRootModel rootModel,
                         @NotNull ModifiableModelsProvider modifiableModelsProvider) {

  }
}
