// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.icons.AllIcons;
import com.intellij.ide.presentation.Presentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.components.JBList;
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
    moduleBuilder.addLibrary("platform-ide", PathUtil.getJarPathForClass(JBList.class));
    moduleBuilder.addLibrary("platform-analysis", PathUtil.getJarPathForClass(Presentation.class));
    moduleBuilder.addLibrary("platform-util-ui", PathUtil.getJarPathForClass(AllIcons.class));
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

  public void testDeprecatedIcon() {
    myFixture.testHighlighting("DeprecatedIcon.java");
  }

  public void testForAbsentIconParam() {
    myFixture.testHighlighting("AbsentIconParam.java");
  }
}
