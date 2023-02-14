// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections;

import com.intellij.icons.AllIcons;
import com.intellij.ide.presentation.Presentation;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.ProjectIconsAccessor;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.components.JBList;
import com.intellij.util.PathUtil;
import org.jetbrains.idea.devkit.inspections.PresentationAnnotationInspection;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;
import org.jetbrains.uast.UField;
import org.jetbrains.uast.UastContextKt;

@TestDataPath("$CONTENT_ROOT/testData/inspections/presentation")
public class KtPresentationAnnotationInspectionTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/presentation";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(PresentationAnnotationInspection.class);
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibrary("platform-ide", PathUtil.getJarPathForClass(JBList.class));
    moduleBuilder.addLibrary("analysis", PathUtil.getJarPathForClass(Presentation.class));
    moduleBuilder.addLibrary("icons", PathUtil.getJarPathForClass(AllIcons.class));
  }

  public void testValidIcon() {
    myFixture.testHighlighting("ValidIcon.kt");

    PsiReference reference = myFixture.getReferenceAtCaretPosition("ValidIcon.kt");
    assertNotNull(reference);
    PsiElement resolved = reference.resolve();
    assertNotNull(resolved);

    PsiField resolvedField = assertInstanceOf(resolved, PsiField.class);
    String qualifiedName = resolvedField.getContainingClass().getQualifiedName();
    assertEquals(AllIcons.Actions.class.getCanonicalName(), qualifiedName);
  }

  public void testProjectIconsAccessorResolveIconFileInKotlin() {
    myFixture.copyFileToProject("customIcon.svg");
    myFixture.configureByFile("CustomIcon.kt");
    PsiElement element = myFixture.getElementAtCaret();
    UField uElement = UastContextKt.toUElement(element, UField.class);

    VirtualFile iconFile = ProjectIconsAccessor.getInstance(getProject()).resolveIconFile(uElement.getUastInitializer());
    assertNotNull(iconFile);
    assertEquals("customIcon.svg", iconFile.getName());
  }
}
