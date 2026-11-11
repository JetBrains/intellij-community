// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.move;

import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.move.moveMembers.MoveMembersOptions;
import com.intellij.refactoring.move.moveMembers.MoveMembersProcessor;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Maxim.Medvedev
 */
@TestApplication
public class GroovyMoveMembersTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "refactoring/move/moveMembers/");
  }

  @Test
  public void testWeirdDeclaration() {
    doTest("A", "B", 0);
  }

  @Test
  public void testScr11871() {
    doTest("pack1.A", "pack1.B", 0);
  }

  @Test
  public void testOuterClassTypeParameters() {
    doTest("pack1.A", "pack2.B", 0);
  }

  @Test
  public void testscr40064() {
    doTest("Test", "Test1", 0);
  }

  @Test
  public void testscr40947() {
    doTest("A", "Test", 0, 1);
  }

  @Test
  public void testIDEADEV11416() {
    doTest("Y", "X", 0);
  }

  @Test
  public void testTwoMethods() {
    doTest("pack1.A", "pack1.C", 0, 1, 2);
  }

  @Test
  public void testIDEADEV12448() {
    doTest("B", "A", 0);
  }

  @Test
  public void testFieldForwardRef() {
    doTest("A", "Constants", 0);
  }

  @Test
  public void testStaticImport() throws Exception {
    doTest("C", "B", 0);
  }

  @Test
  public void testOtherPackageImport() {
    doTest("pack1.ClassWithStaticMethod", "pack2.OtherClass", 1);
  }

  @Test
  public void testEnumConstant() {
    doTest("B", "A", 0);
  }

  @Test
  public void testAliasedImported() {
    doTest("A", "B", 0);
  }

  @Test
  public void testDoc() {
    doTest("A", "B", 0, 1);
  }

  private void doTest(final String sourceClassName, final String targetClassName, final Integer... memberIndices) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final VirtualFile actualDir = myFixture.copyDirectoryToProject(myFixture.getTestName(true) + "/before", "");
    final VirtualFile expectedDir = StandardFileSystems.local().findFileByPath(myFixture.getTestDataPath() + myFixture.getTestName(true) + "/after");
    //final File expectedDir = new File(getTestDataPath() + getTestName(true) + "/after");
    EdtTestUtil.runInEdtAndWait(() -> {
      performAction(sourceClassName, targetClassName, memberIndices);
      try {
        PlatformTestUtil.assertDirectoriesEqual(expectedDir, actualDir);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  private void performAction(String sourceClassName, String targetClassName, Integer[] memberIndices) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final GlobalSearchScope scope = ProjectScope.getProjectScope(myFixture.getProject());
    final JavaPsiFacadeEx facade = myFixture.getJavaFacade();

    GrTypeDefinition sourceClass = (GrTypeDefinition)facade.findClass(sourceClassName, scope);
    assertNotNull(sourceClass, "Class " + sourceClassName + " not found");

    GrTypeDefinition targetClass = (GrTypeDefinition)facade.findClass(targetClassName, scope);
    assertNotNull(targetClass, "Class " + targetClassName + " not found");

    PsiElement[] children = sourceClass.getBody().getChildren();
    ArrayList<PsiMember> members = new ArrayList<>();
    for (PsiElement child : children) {
      if (child instanceof PsiMember) {
        members.add(((PsiMember)child));
      }

      if (child instanceof GrVariableDeclaration variableDeclaration) {
        Collections.addAll(members, variableDeclaration.getMembers());
      }
    }

    LinkedHashSet<PsiMember> memberSet = new LinkedHashSet<>();
    for (int index : memberIndices) {
      PsiMember member = members.get(index);
      assertTrue(member.hasModifierProperty(PsiModifier.STATIC));
      memberSet.add(member);
    }

    final MockMoveMembersOptions options = new MockMoveMembersOptions(targetClass.getQualifiedName(), memberSet);
    options.setMemberVisibility(null);
    new MoveMembersProcessor(myFixture.getProject(), null, options).run();
    UsefulTestCase.doPostponedFormatting(myFixture.getProject());
  }

  private static class MockMoveMembersOptions implements MoveMembersOptions {
    private final PsiMember[] selectedMembers;
    private final String targetClassName;
    @PsiModifier.ModifierConstant
    private String memberVisibility = PsiModifier.PUBLIC;

    private MockMoveMembersOptions(String targetClassName, PsiMember[] selectedMembers) {
      this.selectedMembers = selectedMembers;
      this.targetClassName = targetClassName;
    }

    private MockMoveMembersOptions(String targetClassName, Collection<PsiMember> memberSet) {
      this(targetClassName, DefaultGroovyMethods.asType(memberSet, PsiMember[].class));
    }

    @Override
    public boolean makeEnumConstant() { return true; }

    @Override
    public final PsiMember[] getSelectedMembers() {
      return selectedMembers;
    }

    @Override
    public final String getTargetClassName() {
      return targetClassName;
    }

    @Override
    public String getMemberVisibility() {
      return memberVisibility;
    }

    public void setMemberVisibility(@PsiModifier.ModifierConstant @Nullable String memberVisibility) {
      this.memberVisibility = memberVisibility;
    }
  }
}
