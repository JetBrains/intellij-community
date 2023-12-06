// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IconProvider;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.testIntegration.TestFramework;
import com.intellij.ui.IconManager;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public final class TestIconProvider extends IconProvider {
  @Override
  public Icon getIcon(@NotNull PsiElement element, int flags) {
    if (element.getContainingFile() == null) return null;

    final List<TestFramework> testFrameworks = TestFramework.EXTENSION_NAME.getExtensionList();
    for (TestFramework framework : testFrameworks) {
      if (!isSuitableByLanguage(element, framework)) continue;

      try {
        if (framework.isIgnoredMethod(element)) {
          Icon ignoredTestIcon = AllIcons.RunConfigurations.IgnoredTest;
          LayeredIcon icon = LayeredIcon.layeredIcon(() -> new Icon[]{ignoredTestIcon, PlatformIcons.PUBLIC_ICON});
          icon.setIcon(PlatformIcons.PUBLIC_ICON, 1, ignoredTestIcon.getIconWidth(), 0);
          return icon;
        }
      }
      catch (AbstractMethodError ignored) {}
    }

    for (TestFramework framework : testFrameworks) {
      if (!isSuitableByLanguage(element, framework)) continue;

      try {
        if (framework.isTestMethod(element)) {
          LayeredIcon mark = LayeredIcon.layeredIcon(new Icon[]{IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Method), AllIcons.RunConfigurations.TestMark, PlatformIcons.PUBLIC_ICON});
          mark.setIcon(PlatformIcons.PUBLIC_ICON, 2, IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Method).getIconWidth(), 0);
          return mark;
        }
      }
      catch (AbstractMethodError ignore) {}
    }

    return null;
  }

  private static boolean isSuitableByLanguage(PsiElement element, TestFramework framework) {
    Language frameworkLanguage = framework.getLanguage();
    return frameworkLanguage == Language.ANY || element.getLanguage().isKindOf(frameworkLanguage);
  }
}
