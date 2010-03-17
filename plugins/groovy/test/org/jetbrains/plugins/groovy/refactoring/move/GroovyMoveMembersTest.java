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

package org.jetbrains.plugins.groovy.refactoring.move;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.move.moveMembers.MoveMembersOptions;
import com.intellij.refactoring.move.moveMembers.MoveMembersProcessor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * @author Maxim.Medvedev
 */
public class GroovyMoveMembersTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/move/moveMembers/";
  }

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

  private void doTest(final String sourceClassName, final String targetClassName, final int... memberIndices) throws Exception {
    final VirtualFile actualDir = myFixture.copyDirectoryToProject(getTestName(true) + "/before", "");
    //final VirtualFile expectedDir = LocalFileSystem.getInstance().findFileByPath(getTestDataPath() + getTestName(true) + "/after");
    final File expectedDir = new File(getTestDataPath() + getTestName(true) + "/after");
    performAction(sourceClassName, targetClassName, memberIndices);
    GroovyMoveClassTest.assertDirsEquals(expectedDir, actualDir);
  }

  private void performAction(String sourceClassName, String targetClassName, int[] memberIndices) throws Exception {
    GrTypeDefinition sourceClass = (GrTypeDefinition)myFixture.getJavaFacade().findClass(sourceClassName, ProjectScope.getProjectScope(myFixture.getProject()));
    assertNotNull("Class " + sourceClassName + " not found", sourceClass);
    GrTypeDefinition targetClass = (GrTypeDefinition)myFixture.getJavaFacade().findClass(targetClassName, ProjectScope.getProjectScope(myFixture.getProject()));
    assertNotNull("Class " + targetClassName + " not found", targetClass);

    PsiElement[] children = sourceClass.getBody().getChildren();
    ArrayList<PsiMember> members = new ArrayList<PsiMember>();
    for (PsiElement child : children) {
      if (child instanceof PsiMember) {
        members.add(((PsiMember)child));
      }
      if (child instanceof GrVariableDeclaration) {
        final GrVariableDeclaration variableDeclaration = (GrVariableDeclaration)child;
        for (GrMember member : variableDeclaration.getMembers()) {
          members.add(member);
        }
      }
    }

    LinkedHashSet<PsiMember> memberSet = new LinkedHashSet<PsiMember>();
    for (int index : memberIndices) {
      PsiMember member = members.get(index);
      assertTrue(member.hasModifierProperty(PsiModifier.STATIC));
      memberSet.add(member);
    }

    final MockMoveMembersOptions options = new MockMoveMembersOptions(targetClass.getQualifiedName(), memberSet);
    options.setMemberVisibility(null);
    new MoveMembersProcessor(myFixture.getProject(), null, options).run();
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  class MockMoveMembersOptions implements MoveMembersOptions {
    private final PsiMember[] mySelectedMembers;
    private final String myTargetClassName;
    private String myMemberVisibility = PsiModifier.PUBLIC;

    public MockMoveMembersOptions(String targetClassName, PsiMember[] selectedMembers) {
      mySelectedMembers = selectedMembers;
      myTargetClassName = targetClassName;
    }

    public MockMoveMembersOptions(String targetClassName, Collection<PsiMember> memberSet) {
      this(targetClassName, memberSet.toArray(new PsiMember[memberSet.size()]));
    }

    public String getMemberVisibility() {
      return myMemberVisibility;
    }

    public boolean makeEnumConstant() {
      return true;
    }

    public void setMemberVisibility(String visibility) {
      myMemberVisibility = visibility;
    }

    public PsiMember[] getSelectedMembers() {
      return mySelectedMembers;
    }

    public String getTargetClassName() {
      return myTargetClassName;
    }

  }

}
