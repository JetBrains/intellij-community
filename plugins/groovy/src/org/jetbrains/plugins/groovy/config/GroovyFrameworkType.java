// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.config;

import com.intellij.framework.FrameworkTypeEx;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public class GroovyFrameworkType extends FrameworkTypeEx {
  public GroovyFrameworkType() {
    super("Groovy");
  }

  @Override
  public boolean isDumbAware() {
    return false;
  }

  @NotNull
  @Override
  public FrameworkSupportInModuleProvider createProvider() {
    return new GroovyFrameworkSupportProvider();
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Groovy";
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return JetgroovyIcons.Groovy.Groovy_16x16;
  }
}
