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
          return TestsUIUtil.loadIcon("ignoredTest");
        }
      }
      catch (AbstractMethodError ignored) {}
    }

    for (TestFramework framework : testFrameworks) {
      try {
        if (framework.isTestMethod(element)) {
          return new LayeredIcon(PlatformIcons.METHOD_ICON, TestsUIUtil.loadIcon("testMark"));
        }
      }
      catch (AbstractMethodError ignore) {}
    }

    return null;
  }
}
