// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Registers {@link ProjectTemplate}s.
 *
 * @author Dmitry Avdeev
 * @see com.intellij.ide.util.newProjectWizard.TemplatesGroup
 */
public abstract class ProjectTemplatesFactory {

  public static final ExtensionPointName<ProjectTemplatesFactory> EP_NAME =
    ExtensionPointName.create("com.intellij.projectTemplatesFactory");

  public static final String OTHER_GROUP = "Other";
  public static final String CUSTOM_GROUP = "User-defined";

  public abstract String @NotNull [] getGroups();

  public abstract ProjectTemplate @NotNull [] createTemplates(@Nullable String group, @NotNull WizardContext context);

  public Icon getGroupIcon(String group) {
    return null;
  }

  public int getGroupWeight(String group) {
    return 0;
  }

  public String getParentGroup(String group) {
    return null;
  }
}
