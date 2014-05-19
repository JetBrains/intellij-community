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

import com.intellij.codeInsight.daemon.DefaultGutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import java.util.Collection;
import java.util.Iterator;

@TestDataPath("$CONTENT_ROOT/testData/navigation/descriptionType")
public class DescriptionTypeRelatedItemLineMarkerProviderTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/navigation/descriptionType";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    String pathForClass = PathUtil.getJarPathForClass(LocalInspectionEP.class);
    moduleBuilder.addLibrary("lang-api", pathForClass);
  }

  public void testInspectionDescription() {
    myFixture.copyDirectoryToProject("inspectionDescriptions", "inspectionDescriptions");

    final GutterMark gutter = myFixture.findGutter("MyWithDescriptionInspection.java");
    checkGutterTargets(gutter, "Description", AllIcons.FileTypes.Html, "MyWithDescription.html");
  }

  public void testIntentionDescription() {
    myFixture.copyDirectoryToProject("intentionDescriptions", "intentionDescriptions");

    final Collection<GutterMark> gutters = myFixture.findAllGutters("MyIntentionActionWithDescription.java");
    assertSize(2, gutters);
    final Iterator<GutterMark> it = gutters.iterator();
    checkGutterTargets(it.next(), "Description", AllIcons.FileTypes.Html, "description.html");
    checkGutterTargets(it.next(), "Before/After Templates", AllIcons.Actions.Diff,
                       "after.java.template", "before.java.template");
  }

  private static void checkGutterTargets(final GutterMark gutterMark,
                                         final String tooltip,
                                         final Icon icon,
                                         final String... expectedTargets) {
    assertNotNull(gutterMark);
    assertEquals(tooltip, gutterMark.getTooltipText());
    assertEquals(icon, gutterMark.getIcon());

    final LineMarkerInfo.LineMarkerGutterIconRenderer renderer =
      assertInstanceOf(gutterMark, LineMarkerInfo.LineMarkerGutterIconRenderer.class);
    final LineMarkerInfo lineMarkerInfo = renderer.getLineMarkerInfo();
    final DefaultGutterIconNavigationHandler navigationGutterIconRenderer =
      assertInstanceOf(lineMarkerInfo.getNavigationHandler(), DefaultGutterIconNavigationHandler.class);

    @SuppressWarnings("unchecked")
    final Collection<NavigatablePsiElement> targetElements = navigationGutterIconRenderer.getReferences();

    assertSameElements(ContainerUtil.map(targetElements, new Function<PsiElement, String>() {
      @Override
      public String fun(PsiElement element) {
        return SymbolPresentationUtil.getSymbolPresentableText(element);
      }
    }), expectedTargets);
  }
}