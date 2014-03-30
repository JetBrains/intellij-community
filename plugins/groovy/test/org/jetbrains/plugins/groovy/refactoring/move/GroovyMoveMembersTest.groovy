/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.refactoring.move

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.ProjectScope
import com.intellij.refactoring.move.moveMembers.MoveMembersOptions
import com.intellij.refactoring.move.moveMembers.MoveMembersProcessor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author Maxim.Medvedev
 */
public class GroovyMoveMembersTest extends LightCodeInsightFixtureTestCase {
  final String basePath = TestUtils.testDataPath + "refactoring/move/moveMembers/"

  /*public void testJavadocRefs() throws Exception {
    doTest("Class1", "Class2", 0);
  }*/

  public void testWeirdDeclaration() throws Exception {
    doTest("A", "B", 0);
  }

  //this test is incorrect
  /*public void testInnerClass() throws Exception {
    doTest("A", "B", 0);
  }*/

  public void testScr11871() throws Exception {
    doTest("pack1.A", "pack1.B", 0);
  }

  public void testOuterClassTypeParameters() throws Exception {
    doTest("pack1.A", "pack2.B", 0);
  }

  public void testscr40064() throws Exception {
    doTest("Test", "Test1", 0);
  }

  public void testscr40947() throws Exception {
    doTest("A", "Test", 0, 1);
  }

  public void testIDEADEV11416() throws Exception {
    doTest("Y", "X", 0);
  }

  public void testTwoMethods() throws Exception {
    doTest("pack1.A", "pack1.C", 0, 1, 2);
  }

  public void testIDEADEV12448() throws Exception {
    doTest("B", "A", 0);
  }

  public void testFieldForwardRef() throws Exception {
    doTest("A", "Constants", 0);
  }

  public void testStaticImport() throws Exception {
    doTest("C", "B", 0);
  }

  public void testOtherPackageImport() throws Exception {
    doTest("pack1.ClassWithStaticMethod", "pack2.OtherClass", 1);
  }

  public void testEnumConstant() throws Exception {
    doTest("B", "A", 0);
  }

  public void testAliasedImported() {
    doTest("A", "B", 0);
  }

  public void testDoc() {
    doTest("A", "B", 0, 1);
  }

  private void doTest(final String sourceClassName, final String targetClassName, final int... memberIndices) {
    final VirtualFile actualDir = myFixture.copyDirectoryToProject(getTestName(true) + "/before", "");
    final VirtualFile expectedDir = LocalFileSystem.instance.findFileByPath(testDataPath + getTestName(true) + "/after");
    //final File expectedDir = new File(getTestDataPath() + getTestName(true) + "/after");
    performAction(sourceClassName, targetClassName, memberIndices);
    try {
      PlatformTestUtil.assertDirectoriesEqual(expectedDir, actualDir);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void performAction(String sourceClassName, String targetClassName, int[] memberIndices) {
    final scope = ProjectScope.getProjectScope(myFixture.project)
    final facade = myFixture.javaFacade

    GrTypeDefinition sourceClass = (GrTypeDefinition)facade.findClass(sourceClassName, scope);
    assertNotNull("Class $sourceClassName not found", sourceClass);

    GrTypeDefinition targetClass = (GrTypeDefinition)facade.findClass(targetClassName, scope);
    assertNotNull("Class $targetClassName not found", targetClass);

    PsiElement[] children = sourceClass.body.children;
    ArrayList<PsiMember> members = new ArrayList<PsiMember>();
    for (PsiElement child : children) {
      if (child instanceof PsiMember) {
        members.add(((PsiMember)child));
      }
      if (child instanceof GrVariableDeclaration) {
        final GrVariableDeclaration variableDeclaration = (GrVariableDeclaration)child;
        Collections.addAll(members, variableDeclaration.members);
      }
    }

    LinkedHashSet<PsiMember> memberSet = new LinkedHashSet<PsiMember>();
    for (int index : memberIndices) {
      PsiMember member = members.get(index);
      assertTrue(member.hasModifierProperty(PsiModifier.STATIC));
      memberSet.add(member);
    }

    final MockMoveMembersOptions options = new MockMoveMembersOptions(targetClass.qualifiedName, memberSet);
    options.memberVisibility = null;
    new MoveMembersProcessor(myFixture.project, null, options).run();
    doPostponedFormatting(project)
  }

  private static class MockMoveMembersOptions implements MoveMembersOptions {
    final PsiMember[] selectedMembers;
    final String targetClassName;
    String memberVisibility = PsiModifier.PUBLIC;

    MockMoveMembersOptions(String targetClassName, PsiMember[] selectedMembers) {
      this.selectedMembers = selectedMembers;
      this.targetClassName = targetClassName;
    }

    MockMoveMembersOptions(String targetClassName, Collection<PsiMember> memberSet) {
      this(targetClassName, memberSet as PsiMember[])
    }

    @Override
    boolean makeEnumConstant() { true }
  }

}
