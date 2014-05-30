/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.navigation.NavigationGutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import java.util.Collection;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

public class DevKitGutterTargetsChecker {

  public static void checkGutterTargets(final GutterMark gutterMark,
                                        final String tooltip,
                                        final Icon icon,
                                        final String... expectedTargets) {
    assertNotNull(gutterMark);
    assertEquals(tooltip, gutterMark.getTooltipText());
    assertEquals(icon, gutterMark.getIcon());

    final Collection<PsiElement> targetElements;
    if (gutterMark instanceof LineMarkerInfo.LineMarkerGutterIconRenderer) {
      final LineMarkerInfo.LineMarkerGutterIconRenderer renderer =
        UsefulTestCase.assertInstanceOf(gutterMark, LineMarkerInfo.LineMarkerGutterIconRenderer.class);
      final LineMarkerInfo lineMarkerInfo = renderer.getLineMarkerInfo();
      GutterIconNavigationHandler handler = lineMarkerInfo.getNavigationHandler();

      if (handler instanceof NavigationGutterIconRenderer) {
        targetElements = ((NavigationGutterIconRenderer)handler).getTargetElements();
      }
      else {
        throw new IllegalArgumentException(handler + ": handler not supported");
      }
    }
    else {
      throw new IllegalArgumentException(gutterMark.getClass() + ": gutter not supported");
    }

    UsefulTestCase.assertSameElements(ContainerUtil.map(targetElements, new Function<PsiElement, String>() {
      @Override
      public String fun(PsiElement element) {
        return SymbolPresentationUtil.getSymbolPresentableText(element);
      }
    }), expectedTargets);
  }
}
