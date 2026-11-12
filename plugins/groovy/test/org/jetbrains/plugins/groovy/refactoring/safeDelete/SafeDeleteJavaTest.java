// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.safeDelete;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.bundled.BundledGroovy;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Max Medvedev
 */
@TestApplication
public class SafeDeleteJavaTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  protected final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "refactoring/safeDeleteJavaParameter/");

  @TestDisposable
  protected Disposable testDisposable;

  /** The search resolves classes through the bundled Groovy jar, which a local Bazel run on Windows keeps outside the allowed roots. */
  @BeforeEach
  public void allowBundledGroovyAccess() {
    VfsRootAccess.allowRootAccess(testDisposable, BundledGroovy.getBundledGroovyFile().get().getPath());
  }

  @Test
  public void testGroovyCall() {
    doTest("""
             class A {
               void foo(int ba<caret>r) {}
             }
             """, """
             new A().foo(2)
             """, """
             new A().foo()
             """);
  }

  @Test
  public void testGroovyDocRef() {
    doTest("""
             class A {
               void foo(int ba<caret>r, long baz) {}
             }
             """, """
             /**
             @see A#foo(int,long)
             */
             class X{}
             """, """
             /**
             @see A#foo(long)
             */
             class X{}
             """);
  }

  @Test
  public void testGroovyTypeArgs() {
    doTest("""
             class A<<caret>T> {
             }
             """, """
             class X {
               A<String> a = new A<String>()
             }
             """, """
             class X {
               A a = new A()
             }
             """);
  }

  @Test
  public void testGroovyTypeArgs2() {
    doTest("""
             class A<<caret>T, K> {
             }
             """, """
             class X {
               A<String, String> a = new A<String, String>()
             }
             """, """
             class X {
               A<String> a = new A<String>()
             }
             """);
  }

  private void doTest(String java, String groovy, String groovyAfter) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("test.java", java);
    PsiFile groovyFile = myFixture.addFileToProject("test.groovy", groovy);

    EdtTestUtil.runInEdtAndWait(() -> {
      final PsiElement psiElement = TargetElementUtil
        .findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);

      SafeDeleteHandler.invoke(myFixture.getProject(), new PsiElement[]{psiElement}, true);

      assertEquals(groovyAfter, groovyFile.getText());
    });
  }
}