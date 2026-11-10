/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.internal;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class IconsGeneratedSourcesTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testGeneratedFile() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText("MyIcons.java", """
      package icons;
      /**
       * NOTE THIS FILE IS AUTO-GENERATED
       * DO NOT EDIT IT BY HAND, run build/scripts/icons.gant instead
       */
      public class RubyIcons {}""");
    assertTrue(new IconsGeneratedSourcesFilter().isGeneratedSource(file.getVirtualFile(), myFixture.getProject()));
  }

  @Test
  public void testNewGeneratedFile() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText("MyIcons.java", """
      package icons;
      /**
       * NOTE THIS FILE IS AUTO-GENERATED
       * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
       */
      public class RubyIcons {}""");
    assertTrue(new IconsGeneratedSourcesFilter().isGeneratedSource(file.getVirtualFile(), myFixture.getProject()));
  }

  @Test
  public void testFilterByName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText("MyIconsTest.java", """
      package icons;
      /**
       * NOTE THIS FILE IS AUTO-GENERATED
       * DO NOT EDIT IT BY HAND, run build/scripts/icons.gant instead
       */
      public class RubyIcons {}""");
    assertFalse(new IconsGeneratedSourcesFilter().isGeneratedSource(file.getVirtualFile(), myFixture.getProject()));
  }

  @Test
  public void testFilterByComment() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText("MyIcons.java", "package icons;\npublic class RubyIcons {}");
    assertFalse(new IconsGeneratedSourcesFilter().isGeneratedSource(file.getVirtualFile(), myFixture.getProject()));
  }
}
