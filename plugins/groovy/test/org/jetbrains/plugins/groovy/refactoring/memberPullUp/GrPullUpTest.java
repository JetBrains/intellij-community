// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.memberPullUp;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.listeners.JavaRefactoringListenerManager;
import com.intellij.refactoring.listeners.MoveMemberListener;
import com.intellij.refactoring.memberPullUp.PullUpProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * Created by Max Medvedev on 8/17/13
 */
public class GrPullUpTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/pullUp";
  }

  public void testQualifiedThis() {
    doTest(new MemberDescriptor("Inner", PsiClass.class));
  }

  public void testQualifiedSuper() {
    doTest(new MemberDescriptor("Inner", PsiClass.class));
  }

  public void testQualifiedReference() { // IDEADEV-25008
    doTest(new MemberDescriptor("getX", PsiMethod.class),
           new MemberDescriptor("setX", PsiMethod.class),
           new MemberDescriptor("x", PsiField.class));
  }

  public void testPullUpInheritedStaticClasses() {
    doTest(new MemberDescriptor("C", PsiClass.class),
           new MemberDescriptor("D", PsiClass.class));
  }

  public void testPullUpPrivateInnerClassWithPrivateConstructor() {
    doTest(new MemberDescriptor("C", PsiClass.class));
  }

  public void testPullUpAndMakeAbstract() {
    doTest(new MemberDescriptor("a", PsiMethod.class),
           new MemberDescriptor("b", PsiMethod.class, true));
  }

  public void _testTryCatchFieldInitializer() {
    doTest(new MemberDescriptor("field", PsiField.class));
  }

  public void testIfFieldInitializationWithNonMovedField() {
    doTest(new MemberDescriptor("f", PsiField.class));
  }

  public void _testIfFieldMovedInitialization() {
    doTest(new MemberDescriptor("f", PsiField.class));
  }

  public void _testMultipleConstructorsFieldInitialization() {
    doTest(new MemberDescriptor("f", PsiField.class));
  }

  public void _testMultipleConstructorsFieldInitializationNoGood() {
    doTest(new MemberDescriptor("f", PsiField.class));
  }

  public void _testRemoveOverride() {
    doTest(new MemberDescriptor("get", PsiMethod.class));
  }

  public void testTypeParamErasure() {
    doTest(new MemberDescriptor("f", PsiField.class));
  }

  public void testTypeParamSubst() {
    doTest(new MemberDescriptor("f", PsiField.class));
  }

  public void testTypeArgument() {
    doTest(new MemberDescriptor("f", PsiField.class));
  }

  public void testGenericsInAbstractMethod() {
    doTest(new MemberDescriptor("method", PsiMethod.class, true));
  }

  public void _testReplaceDuplicatesInInheritors() {
    doTest(new MemberDescriptor("foo", PsiMethod.class, false));
  }

  public void testGenericsInImplements() {
    doTest(false, new MemberDescriptor("I", PsiClass.class));
  }

  public void testUpdateStaticRefs() {
    doTest(false, new MemberDescriptor("foo", PsiMethod.class));
  }

  public void testRemoveOverrideFromPulledMethod() {
    doTest(false, new MemberDescriptor("foo", PsiMethod.class));
  }

  public void testPreserveOverrideInPulledMethod() {
    doTest(false, new MemberDescriptor("foo", PsiMethod.class));
  }

  public void testMergeInterfaces() {
    doTest(false, new MemberDescriptor("I", PsiClass.class));
  }

  public void testTypeParamsConflictingNames() {
    doTest(false, new MemberDescriptor("foo", PsiMethod.class));
  }

  public void testEscalateVisibility() {
    doTest(false, new MemberDescriptor("foo", PsiMethod.class));
  }

  public void testPreserveOverride() {
    doTest(false, new MemberDescriptor("foo", PsiMethod.class));
  }

  public void testImplementsList1() {
    doTest(false, new MemberDescriptor("I1", PsiClass.class));
  }

  public void testImplementsList2() {
    doTest(false, new MemberDescriptor("I2", PsiClass.class));
  }

  public void testImplementsList3() {
    doTest(false, new MemberDescriptor("I1", PsiClass.class), new MemberDescriptor("I2", PsiClass.class));
  }

  public void testDocCommentInMethod() {
    doTest(false, new MemberDescriptor("foo", PsiMethod.class));
  }

  public void testSupers() {
    doTest(false, new MemberDescriptor("bar", PsiMethod.class));
  }

  private void doTest(MemberDescriptor... membersToFind) {
    doTest(true, membersToFind);
  }

  private void doTest(final boolean checkMembersMovedCount, MemberDescriptor... membersToFind) {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    PsiElement elementAt = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    final PsiClass sourceClass = PsiTreeUtil.getParentOfType(elementAt, PsiClass.class);
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

    final int[] countMoved = new int[]{0};
    final MoveMemberListener listener = new MoveMemberListener() {
      @Override
      public void memberMoved(@NotNull PsiClass aClass, @NotNull PsiMember member) {
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
    MemberInfo[] infos = new MemberInfo[membersToFind.length];
    for (int i = 0; i < membersToFind.length; i++) {
      final Class<? extends PsiMember> clazz = membersToFind[i].myClass;
      final String name = membersToFind[i].myName;
      PsiMember member = null;
      boolean overrides = false;
      GrReferenceList refList = null;
      if (PsiClass.class.isAssignableFrom(clazz)) {
        member = sourceClass.findInnerClassByName(name, false);
        if (member == null) {
          final PsiClass[] supers = sourceClass.getSupers();
          for (PsiClass superTypeClass : supers) {
            if (superTypeClass.getName().equals(name)) {
              member = superTypeClass;
              overrides = true;
              refList = superTypeClass.isInterface()
                        ? ((GrTypeDefinition)sourceClass).getImplementsClause()
                        : ((GrTypeDefinition)sourceClass).getExtendsClause();
              break;
            }
          }
        }
      }
      else if (PsiMethod.class.isAssignableFrom(clazz)) {
        final PsiMethod[] methods = sourceClass.findMethodsByName(name, false);
        assertEquals(1, methods.length);
        member = methods[0];
      }
      else if (PsiField.class.isAssignableFrom(clazz)) {
        member = sourceClass.findFieldByName(name, false);
      }

      assertNotNull(member);
      assertInstanceOf(member, GrMember.class);
      infos[i] = new MemberInfo(member, overrides, refList);
      infos[i].setToAbstract(membersToFind[i].myAbstract);
    }
    return infos;
  }
}