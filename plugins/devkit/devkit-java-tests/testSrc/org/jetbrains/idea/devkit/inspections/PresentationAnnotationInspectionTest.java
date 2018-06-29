/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.icons.AllIcons;
import com.intellij.ide.presentation.Presentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/presentation")
public class PresentationAnnotationInspectionTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/presentation";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(PresentationAnnotationInspection.class);
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    String presentationJar = PathUtil.getJarPathForClass(Presentation.class);
    moduleBuilder.addLibrary("presentation", presentationJar);
    String iconsJar = PathUtil.getJarPathForClass(AllIcons.class);
    moduleBuilder.addLibrary("icons", iconsJar);
  }


  public void testValidIcon() {
    myFixture.testHighlighting("ValidIcon.java");

    PsiReference reference = myFixture.getReferenceAtCaretPosition("ValidIcon.java");
    assertNotNull(reference);
    PsiElement resolved = reference.resolve();
    assertNotNull(resolved);

    PsiField resolvedField = assertInstanceOf(resolved, PsiField.class);
    String qualifiedName = resolvedField.getContainingClass().getQualifiedName();
    assertEquals(AllIcons.Actions.class.getCanonicalName(), qualifiedName);
  }

  public void testInvalidIcon() {
    myFixture.testHighlighting("InvalidIcon.java");
  }

  public void testForAbsentIconParam() {
    myFixture.testHighlighting("AbsentIconParam.java");
  }
}