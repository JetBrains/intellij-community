// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.Assert;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ResolveClassInNewTest extends LightGroovyTestCase {

  @NotNull
  private final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST;

  @NotNull
  private final String basePath = TestUtils.getTestDataPath() + "/resolve/classInNew";

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return projectDescriptor;
  }

  @Override
  public final @NotNull String getBasePath() {
    return basePath;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    RecursionManager.assertOnRecursionPrevention(getTestRootDisposable());
  }

  private void doDirectoryTest(String fqn) {
    final String name = getTestName();
    getFixture().copyDirectoryToProject(name + "/src", "/");
    getFixture().configureFromTempProjectFile("/somePackage/test.groovy");
    final PsiReference reference = getFile().findReferenceAt(getEditor().getCaretModel().getOffset());
    final PsiElement resolved = reference.resolve();
    if (fqn == null) {
      Assert.assertNull(resolved);
      return;
    }

    PsiClass currentClass = UsefulTestCase.assertInstanceOf(resolved, PsiClass.class);
    String[] parts = fqn.split("\\$");
    String outerFqn = parts[0];
    List<String> inners = Arrays.asList(parts).subList(1, parts.length);
    Collections.reverse(inners);
    for (String part : inners) {
      Assert.assertEquals(part, currentClass.getName());
      currentClass = currentClass.getContainingClass();
    }

    Assert.assertNull(currentClass.getContainingClass());
    Assert.assertEquals(outerFqn, currentClass.getQualifiedName());
  }

  public void testInnerClassOfCurrentClass() {
    doDirectoryTest("somePackage.OuterOuter$Outer$Current$Target");
  }

  public void testInnerClassOfCurrentInterface() {
    doDirectoryTest("somePackage.CurrentI$Target");
  }

  public void testInnerClassOfCurrentInterfaceOutside() {
    doDirectoryTest("somePackage.CurrentIOutside$Target");
  }

  public void testInnerClassOfSuperclass() {
    doDirectoryTest("somePackage.CurrentParent$Target");
  }

  public void testInnerClassOfSuperclassInterface() {
    doDirectoryTest("somePackage.CurrentParentI$Target");
  }

  public void testInnerClassOfSuperclassInterfaceOutside() {
    doDirectoryTest("somePackage.CurrentParentIOutside$Target");
  }

  public void testInnerClassOfSuperclassOutside() {
    doDirectoryTest("somePackage.CurrentParentOutside$Target");
  }

  public void testInnerClassOfSuperclassOutsideInterface() {
    doDirectoryTest("somePackage.CurrentParentOutsideI$Target");
  }

  public void testInnerClassOfOutermostClass() {
    doDirectoryTest("somePackage.OuterOuter$Target");
  }

  public void testInnerClassOfOutermostClassInterface() {
    doDirectoryTest("somePackage.OuterOuterI$Target");
  }

  public void testInnerClassOfOuterClass() {
    doDirectoryTest("somePackage.OuterOuter$Outer$Target");
  }

  public void testInnerClassOfOuterClassInterface() {
    doDirectoryTest("somePackage.OuterI$Target");
  }

  public void testInnerClassOfOuterClassInterfaceOutside() {
    doDirectoryTest("somePackage.OuterIOutside$Target");
  }

  public void testClassInSameFile() {
    doDirectoryTest("somePackage.Target");
  }

  public void testClassRegularImport() {
    doDirectoryTest("unrelatedPackage.Target");
  }

  public void testClassRegularImport2() {
    doDirectoryTest("unrelatedPackage.Target2");
  }

  public void testClassStaticImport() {
    doDirectoryTest("unrelatedPackage.Container$Target2");
  }

  public void testClassStaticImport2() {
    doDirectoryTest("unrelatedPackage.Container$Target");
  }

  public void testClassInSamePackage() {
    doDirectoryTest("somePackage.Target");
  }

  public void testClassStarImport() {
    doDirectoryTest("anotherUnrelatedPackage.Target");
  }

  public void testClassStarImport2() {
    doDirectoryTest("unrelatedPackage.Target");
  }

  public void testClassStaticStarImport() {
    doDirectoryTest("unrelatedPackage.Container$Target");
  }

  public void testClassStaticStarImport2() {
    doDirectoryTest("unrelatedPackage.Container2$Target");
  }

  public void testDoNotResolveOthers() {
    doDirectoryTest(null);
  }
}
