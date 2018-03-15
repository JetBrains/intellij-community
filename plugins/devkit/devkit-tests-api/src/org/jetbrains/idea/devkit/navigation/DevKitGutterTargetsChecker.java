// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.navigation.NavigationGutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.testFramework.UsefulTestCase;
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

    UsefulTestCase.assertSameElements(ContainerUtil.map(targetElements, element -> SymbolPresentationUtil.getSymbolPresentableText(element)), expectedTargets);
  }
}
