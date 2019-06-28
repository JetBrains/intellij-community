// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IconProvider;
import com.intellij.psi.PsiElement;
import com.intellij.testIntegration.TestFramework;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public class TestIconProvider extends IconProvider {
  @Override
  public Icon getIcon(@NotNull PsiElement element, int flags) {
    final List<TestFramework> testFrameworks = TestFramework.EXTENSION_NAME.getExtensionList();

    for (TestFramework framework : testFrameworks) {
      try {
        if (framework.isIgnoredMethod(element)) {
          final Icon ignoredTestIcon = AllIcons.RunConfigurations.IgnoredTest;
          final LayeredIcon icon = new LayeredIcon(ignoredTestIcon, PlatformIcons.PUBLIC_ICON);
          icon.setIcon(PlatformIcons.PUBLIC_ICON, 1, ignoredTestIcon.getIconWidth(), 0);
          return icon;
        }
      }
      catch (AbstractMethodError ignored) {}
    }

    for (TestFramework framework : testFrameworks) {
      try {
        if (framework.isTestMethod(element)) {
          LayeredIcon mark = new LayeredIcon(PlatformIcons.METHOD_ICON, AllIcons.RunConfigurations.TestMark, PlatformIcons.PUBLIC_ICON);
          mark.setIcon(PlatformIcons.PUBLIC_ICON, 2, PlatformIcons.METHOD_ICON.getIconWidth(), 0);
          return mark;
        }
      }
      catch (AbstractMethodError ignore) {}
    }

    return null;
  }
}
