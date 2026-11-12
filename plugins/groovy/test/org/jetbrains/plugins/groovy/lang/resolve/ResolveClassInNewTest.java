// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyFixtures;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class ResolveClassInNewTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "/resolve/classInNew");
    RecursionManager.assertOnRecursionPrevention(myFixture.getTestRootDisposable());
  }

  private void doDirectoryTest(String fqn) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String name = LightGroovyFixtures.groovyTestName(myFixture);
    myFixture.copyDirectoryToProject(name + "/src", "/");
    myFixture.configureFromTempProjectFile("/somePackage/test.groovy");
    ReadAction.runBlocking(() -> {
      final PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
      final PsiElement resolved = reference.resolve();
      if (fqn == null) {
        assertNull(resolved);
        return;
      }

      PsiClass currentClass = assertInstanceOf(PsiClass.class, resolved);
      String[] parts = fqn.split("\\$");
      String outerFqn = parts[0];
      List<String> inners = Arrays.asList(parts).subList(1, parts.length);
      Collections.reverse(inners);
      for (String part : inners) {
        assertEquals(part, currentClass.getName());
        currentClass = currentClass.getContainingClass();
      }

      assertNull(currentClass.getContainingClass());
      assertEquals(outerFqn, currentClass.getQualifiedName());
    });
  }

  @Test
  public void testInnerClassOfCurrentClass() {
    doDirectoryTest("somePackage.OuterOuter$Outer$Current$Target");
  }

  @Test
  public void testInnerClassOfCurrentInterface() {
    doDirectoryTest("somePackage.CurrentI$Target");
  }

  @Test
  public void testInnerClassOfCurrentInterfaceOutside() {
    doDirectoryTest("somePackage.CurrentIOutside$Target");
  }

  @Test
  public void testInnerClassOfSuperclass() {
    doDirectoryTest("somePackage.CurrentParent$Target");
  }

  @Test
  public void testInnerClassOfSuperclassInterface() {
    doDirectoryTest("somePackage.CurrentParentI$Target");
  }

  @Test
  public void testInnerClassOfSuperclassInterfaceOutside() {
    doDirectoryTest("somePackage.CurrentParentIOutside$Target");
  }

  @Test
  public void testInnerClassOfSuperclassOutside() {
    doDirectoryTest("somePackage.CurrentParentOutside$Target");
  }

  @Test
  public void testInnerClassOfSuperclassOutsideInterface() {
    doDirectoryTest("somePackage.CurrentParentOutsideI$Target");
  }

  @Test
  public void testInnerClassOfOutermostClass() {
    doDirectoryTest("somePackage.OuterOuter$Target");
  }

  @Test
  public void testInnerClassOfOutermostClassInterface() {
    doDirectoryTest("somePackage.OuterOuterI$Target");
  }

  @Test
  public void testInnerClassOfOuterClass() {
    doDirectoryTest("somePackage.OuterOuter$Outer$Target");
  }

  @Test
  public void testInnerClassOfOuterClassInterface() {
    doDirectoryTest("somePackage.OuterI$Target");
  }

  @Test
  public void testInnerClassOfOuterClassInterfaceOutside() {
    doDirectoryTest("somePackage.OuterIOutside$Target");
  }

  @Test
  public void testClassInSameFile() {
    doDirectoryTest("somePackage.Target");
  }

  @Test
  public void testClassRegularImport() {
    doDirectoryTest("unrelatedPackage.Target");
  }

  @Test
  public void testClassRegularImport2() {
    doDirectoryTest("unrelatedPackage.Target2");
  }

  @Test
  public void testClassStaticImport() {
    doDirectoryTest("unrelatedPackage.Container$Target2");
  }

  @Test
  public void testClassStaticImport2() {
    doDirectoryTest("unrelatedPackage.Container$Target");
  }

  @Test
  public void testClassInSamePackage() {
    doDirectoryTest("somePackage.Target");
  }

  @Test
  public void testClassStarImport() {
    doDirectoryTest("anotherUnrelatedPackage.Target");
  }

  @Test
  public void testClassStarImport2() {
    doDirectoryTest("unrelatedPackage.Target");
  }

  @Test
  public void testClassStaticStarImport() {
    doDirectoryTest("unrelatedPackage.Container$Target");
  }

  @Test
  public void testClassStaticStarImport2() {
    doDirectoryTest("unrelatedPackage.Container2$Target");
  }

  @Test
  public void testDoNotResolveOthers() {
    doDirectoryTest(null);
  }
}
