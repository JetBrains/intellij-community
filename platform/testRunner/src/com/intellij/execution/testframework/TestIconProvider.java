/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.execution.testframework;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IconProvider;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.testIntegration.TestFramework;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * User: anna
 */
public class TestIconProvider extends IconProvider {
  @Override
  public Icon getIcon(@NotNull PsiElement element, int flags) {
    final TestFramework[] testFrameworks = Extensions.getExtensions(TestFramework.EXTENSION_NAME);

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
          final LayeredIcon mark = new LayeredIcon(PlatformIcons.METHOD_ICON, AllIcons.RunConfigurations.TestMark, PlatformIcons.PUBLIC_ICON);
          mark.setIcon(PlatformIcons.PUBLIC_ICON, 2, PlatformIcons.METHOD_ICON.getIconWidth(), 0);
          return mark;
        }
      }
      catch (AbstractMethodError ignore) {}
    }

    return null;
  }
}
