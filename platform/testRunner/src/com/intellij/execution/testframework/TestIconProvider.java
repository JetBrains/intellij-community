// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IconProvider;
import com.intellij.lang.Language;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.testIntegration.TestFramework;
import com.intellij.ui.IconManager;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.BitUtil;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

@ApiStatus.Internal
public final class TestIconProvider extends IconProvider {
  @Override
  public Icon getIcon(@NotNull PsiElement element, int flags) {
    if (element.getContainingFile() == null) return null;

    final List<TestFramework> testFrameworks = DumbService.getDumbAwareExtensions(element.getProject(), TestFramework.EXTENSION_NAME);
    for (TestFramework framework : testFrameworks) {
      if (!isSuitableByLanguage(element, framework)) continue;

      try {
        if (framework.isIgnoredMethod(element)) {
          Icon ignoredTestIcon = AllIcons.RunConfigurations.IgnoredTest;
          if (BitUtil.isSet(flags, Iconable.ICON_FLAG_VISIBILITY)) {
            LayeredIcon icon = LayeredIcon.layeredIcon(() -> new Icon[]{ignoredTestIcon, PlatformIcons.PUBLIC_ICON});
            icon.setIcon(PlatformIcons.PUBLIC_ICON, 1, ignoredTestIcon.getIconWidth(), 0);
            return icon;
          }
          else {
            return ignoredTestIcon;
          }
        }
      }
      catch (AbstractMethodError ignored) {}
    }

    for (TestFramework framework : testFrameworks) {
      if (!isSuitableByLanguage(element, framework)) continue;

      try {
        if (framework.isTestMethod(element)) {
          LayeredIcon mark;
          if (BitUtil.isSet(flags, Iconable.ICON_FLAG_VISIBILITY)) {
            mark = LayeredIcon.layeredIcon(new Icon[]{IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Method), AllIcons.RunConfigurations.TestMark, PlatformIcons.PUBLIC_ICON});
            mark.setIcon(PlatformIcons.PUBLIC_ICON, 2, IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Method).getIconWidth(), 0);
          }
          else {
            mark = LayeredIcon.layeredIcon(new Icon[]{IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Method), AllIcons.RunConfigurations.TestMark});
          }
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
