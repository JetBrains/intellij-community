// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.config;

import com.intellij.framework.FrameworkTypeEx;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;

import javax.swing.*;

public final class GroovyFrameworkType extends FrameworkTypeEx {
  public GroovyFrameworkType() {
    super("Groovy");
  }

  @Override
  public boolean isDumbAware() {
    return false;
  }

  @Override
  public @NotNull FrameworkSupportInModuleProvider createProvider() {
    return new GroovyFrameworkSupportProvider();
  }

  @Override
  public @NotNull String getPresentableName() {
    return GroovyBundle.message("language.groovy");
  }

  @Override
  public @NotNull Icon getIcon() {
    return JetgroovyIcons.Groovy.Groovy_16x16;
  }
}
