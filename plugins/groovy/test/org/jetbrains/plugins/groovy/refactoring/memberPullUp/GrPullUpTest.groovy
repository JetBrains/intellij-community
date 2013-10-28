/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.memberPullUp

import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.listeners.JavaRefactoringListenerManager
import com.intellij.refactoring.listeners.MoveMemberListener
import com.intellij.refactoring.memberPullUp.PullUpProcessor
import com.intellij.refactoring.util.DocCommentPolicy
import com.intellij.refactoring.util.classMembers.MemberInfo
import com.intellij.util.ui.UIUtil
import junit.framework.Assert
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * Created by Max Medvedev on 8/17/13
 */
class GrPullUpTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() {
    TestUtils.testDataPath + 'refactoring/pullUp'
  }

  public void testQualifiedThis() {
    doTest(new MemberDescriptor("Inner", PsiClass));
  }

  public void testQualifiedSuper() {
    doTest(new MemberDescriptor("Inner", PsiClass));
  }

  public void testQualifiedReference() {     // IDEADEV-25008
    doTest(new MemberDescriptor("getX", PsiMethod),
           new MemberDescriptor("setX", PsiMethod),
           new MemberDescriptor("x", PsiField));
  }

  public void testPullUpInheritedStaticClasses() {
    doTest(new MemberDescriptor("C", PsiClass),
           new MemberDescriptor("D", PsiClass));
  }

  public void testPullUpPrivateInnerClassWithPrivateConstructor() {
    doTest(new MemberDescriptor("C", PsiClass));
  }

  public void testPullUpAndMakeAbstract() {
    doTest(new MemberDescriptor("a", PsiMethod),
           new MemberDescriptor("b", PsiMethod, true));
  }

  public void _testTryCatchFieldInitializer() {
    doTest(new MemberDescriptor("field", PsiField));
  }

  public void testIfFieldInitializationWithNonMovedField() {
    doTest(new MemberDescriptor("f", PsiField));
  }

  public void _testIfFieldMovedInitialization() {
    doTest(new MemberDescriptor("f", PsiField));
  }

  public void _testMultipleConstructorsFieldInitialization() {
    doTest(new MemberDescriptor("f", PsiField));
  }

  public void _testMultipleConstructorsFieldInitializationNoGood() {
    doTest(new MemberDescriptor("f", PsiField));
  }

  public void _testRemoveOverride() {
    doTest(new MemberDescriptor("get", PsiMethod));
  }

  public void testTypeParamErasure() {
    doTest(new MemberDescriptor("f", PsiField));
  }

  public void testTypeParamSubst() {
    doTest(new MemberDescriptor("f", PsiField));
  }

  public void testTypeArgument() {
    doTest(new MemberDescriptor("f", PsiField));
  }

  public void testGenericsInAbstractMethod() {
    doTest(new MemberDescriptor("method", PsiMethod, true));
  }

  public void _testReplaceDuplicatesInInheritors() {
    doTest(new MemberDescriptor("foo", PsiMethod, false));
  }

  public void testGenericsInImplements() {
    doTest(false, new MemberDescriptor("I", PsiClass));
  }

  public void testUpdateStaticRefs() {
    doTest(false, new MemberDescriptor("foo", PsiMethod));
  }

  public void testRemoveOverrideFromPulledMethod() {
    doTest(false, new MemberDescriptor("foo", PsiMethod));
  }

  public void testPreserveOverrideInPulledMethod() {
    doTest(false, new MemberDescriptor("foo", PsiMethod));
  }

  public void testMergeInterfaces() {
    doTest(false, new MemberDescriptor("I", PsiClass));
  }

  public void testTypeParamsConflictingNames() {
    doTest(false, new MemberDescriptor("foo", PsiMethod));
  }

  public void testEscalateVisibility() {
    doTest(false, new MemberDescriptor("foo", PsiMethod));
  }

  public void testPreserveOverride() {
    doTest(false, new MemberDescriptor("foo", PsiMethod));
  }

  void testImplementsList1() {
    doTest(false, new MemberDescriptor("I1", PsiClass))
  }

  void testImplementsList2() {
    doTest(false, new MemberDescriptor("I2", PsiClass))
  }

  void testImplementsList3() {
    doTest(false, new MemberDescriptor("I1", PsiClass), new MemberDescriptor("I2", PsiClass))
  }

  void testDocCommentInMethod() {
    doTest(false, new MemberDescriptor("foo", PsiMethod))
  }

  void testSupers() {
    doTest(false, new MemberDescriptor("bar", PsiMethod))
  }

  private void doTest(MemberDescriptor... membersToFind) {
    doTest(true, membersToFind);
  }

  private void doTest(final boolean checkMembersMovedCount, MemberDescriptor... membersToFind) {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    PsiElement elementAt = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    final PsiClass sourceClass = PsiTreeUtil.getParentOfType(elementAt, PsiClass);
    assertNotNull(sourceClass);

    PsiClass targetClass = sourceClass.getSuperClass();
    assertNotNull(targetClass);
    if (!targetClass.isWritable()) {
      final PsiClass[] interfaces = sourceClass.getInterfaces();
      assertEquals(2, interfaces.length);
      assertTrue(interfaces[0].isWritable());
      targetClass = interfaces[0];
    }
    MemberInfo[] infos = findMembers(sourceClass, membersToFind);

    final int[] countMoved = [0];
    final MoveMemberListener listener = new MoveMemberListener() {
      @Override
      public void memberMoved(PsiClass aClass, PsiMember member) {
        assertEquals(sourceClass, aClass);
        countMoved[0]++;
      }
    };
    JavaRefactoringListenerManager.getInstance(getProject()).addMoveMembersListener(listener);
    final PullUpProcessor helper = new PullUpProcessor(sourceClass, targetClass, infos, new DocCommentPolicy(DocCommentPolicy.ASIS));
    helper.run();
    UIUtil.dispatchAllInvocationEvents();
    JavaRefactoringListenerManager.getInstance(getProject()).removeMoveMembersListener(listener);
    if (checkMembersMovedCount) {
      assertEquals(countMoved[0], membersToFind.length);
    }
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  public static class MemberDescriptor {
    private String myName;
    private Class<? extends PsiMember> myClass;
    private boolean myAbstract;

    public MemberDescriptor(String name, Class<? extends PsiMember> aClass, boolean isAbstract) {
      myName = name;
      myClass = aClass;
      myAbstract = isAbstract;
    }


    public MemberDescriptor(String name, Class<? extends PsiMember> aClass) {
      this(name, aClass, false);
    }
  }

  public static MemberInfo[] findMembers(final PsiClass sourceClass, final MemberDescriptor... membersToFind) {
    MemberInfo[] infos = new MemberInfo[membersToFind.length]
    for (int i = 0; i < membersToFind.length; i++) {
      final Class<? extends PsiMember> clazz = membersToFind[i].myClass
      final String name = membersToFind[i].myName
      PsiMember member = null
      boolean overrides = false
      GrReferenceList refList = null
      if (PsiClass.isAssignableFrom(clazz)) {
        member = sourceClass.findInnerClassByName(name, false)
        if (member == null) {
          final PsiClass[] supers = sourceClass.getSupers()
          for (PsiClass superTypeClass : supers) {
            if (superTypeClass.getName().equals(name)) {
              member = superTypeClass
              overrides = true
              refList = superTypeClass.isInterface() ?
                        (sourceClass as GrTypeDefinition).getImplementsClause() :
                        (sourceClass as GrTypeDefinition).getExtendsClause()
              break
            }
          }
        }

      } else if (PsiMethod.class.isAssignableFrom(clazz)) {
        final PsiMethod[] methods = sourceClass.findMethodsByName(name, false)
        Assert.assertEquals(1, methods.length)
        member = methods[0]
      } else if (PsiField.class.isAssignableFrom(clazz)) {
        member = sourceClass.findFieldByName(name, false)
      }

      assertNotNull(member)
      assertInstanceOf(member, GrMember)
      infos[i] = new MemberInfo(member as GrMember, overrides, refList)
      infos[i].setToAbstract(membersToFind[i].myAbstract)
    }
    return infos
  }
}
