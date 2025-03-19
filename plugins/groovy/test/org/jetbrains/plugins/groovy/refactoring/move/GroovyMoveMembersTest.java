// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.move;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.move.moveMembers.MoveMembersOptions;
import com.intellij.refactoring.move.moveMembers.MoveMembersProcessor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import junit.framework.TestCase;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;

/**
 * @author Maxim.Medvedev
 */
public class GroovyMoveMembersTest extends LightJavaCodeInsightFixtureTestCase {
  public void testWeirdDeclaration() {
    doTest("A", "B", 0);
  }

  public void testScr11871() {
    doTest("pack1.A", "pack1.B", 0);
  }

  public void testOuterClassTypeParameters() {
    doTest("pack1.A", "pack2.B", 0);
  }

  public void testscr40064() {
    doTest("Test", "Test1", 0);
  }

  public void testscr40947() {
    doTest("A", "Test", 0, 1);
  }

  public void testIDEADEV11416() {
    doTest("Y", "X", 0);
  }

  public void testTwoMethods() {
    doTest("pack1.A", "pack1.C", 0, 1, 2);
  }

  public void testIDEADEV12448() {
    doTest("B", "A", 0);
  }

  public void testFieldForwardRef() {
    doTest("A", "Constants", 0);
  }

  public void testStaticImport() throws Exception {
    doTest("C", "B", 0);
  }

  public void testOtherPackageImport() {
    doTest("pack1.ClassWithStaticMethod", "pack2.OtherClass", 1);
  }

  public void testEnumConstant() {
    doTest("B", "A", 0);
  }

  public void testAliasedImported() {
    doTest("A", "B", 0);
  }

  public void testDoc() {
    doTest("A", "B", 0, 1);
  }

  private void doTest(final String sourceClassName, final String targetClassName, final Integer... memberIndices) {
    final VirtualFile actualDir = myFixture.copyDirectoryToProject(getTestName(true) + "/before", "");
    final VirtualFile expectedDir = LocalFileSystem.getInstance().findFileByPath(getTestDataPath() + getTestName(true) + "/after");
    //final File expectedDir = new File(getTestDataPath() + getTestName(true) + "/after");
    performAction(sourceClassName, targetClassName, memberIndices);
    try {
      PlatformTestUtil.assertDirectoriesEqual(expectedDir, actualDir);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void performAction(String sourceClassName, String targetClassName, Integer[] memberIndices) {
    final GlobalSearchScope scope = ProjectScope.getProjectScope(myFixture.getProject());
    final JavaPsiFacadeEx facade = myFixture.getJavaFacade();

    GrTypeDefinition sourceClass = (GrTypeDefinition)facade.findClass(sourceClassName, scope);
    TestCase.assertNotNull("Class " + sourceClassName + " not found", sourceClass);

    GrTypeDefinition targetClass = (GrTypeDefinition)facade.findClass(targetClassName, scope);
    TestCase.assertNotNull("Class " + targetClassName + " not found", targetClass);

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
      TestCase.assertTrue(member.hasModifierProperty(PsiModifier.STATIC));
      memberSet.add(member);
    }


    final MockMoveMembersOptions options = new MockMoveMembersOptions(targetClass.getQualifiedName(), memberSet);
    options.setMemberVisibility(null);
    new MoveMembersProcessor(myFixture.getProject(), null, options).run();
    UsefulTestCase.doPostponedFormatting(getProject());
  }

  @Override
  public final String getBasePath() {
    return basePath;
  }

  private final String basePath = TestUtils.getTestDataPath() + "refactoring/move/moveMembers/";

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
