// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.memberPullUp;

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.listeners.JavaRefactoringListenerManager;
import com.intellij.refactoring.listeners.MoveMemberListener;
import com.intellij.refactoring.memberPullUp.PullUpProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by Max Medvedev on 8/17/13
 */
@TestApplication
public class GrPullUpTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "refactoring/pullUp");

  @Test
  public void testQualifiedThis() {
    doTest(new MemberDescriptor("Inner", PsiClass.class));
  }

  @Test
  public void testQualifiedSuper() {
    doTest(new MemberDescriptor("Inner", PsiClass.class));
  }

  @Test
  public void testQualifiedReference() { // IDEADEV-25008
    doTest(new MemberDescriptor("getX", PsiMethod.class),
           new MemberDescriptor("setX", PsiMethod.class),
           new MemberDescriptor("x", PsiField.class));
  }

  @Test
  public void testPullUpInheritedStaticClasses() {
    doTest(new MemberDescriptor("C", PsiClass.class),
           new MemberDescriptor("D", PsiClass.class));
  }

  @Test
  public void testPullUpPrivateInnerClassWithPrivateConstructor() {
    doTest(new MemberDescriptor("C", PsiClass.class));
  }

  @Test
  public void testPullUpAndMakeAbstract() {
    doTest(new MemberDescriptor("a", PsiMethod.class),
           new MemberDescriptor("b", PsiMethod.class, true));
  }

  public void _testTryCatchFieldInitializer() {
    doTest(new MemberDescriptor("field", PsiField.class));
  }

  @Test
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

  @Test
  public void testTypeParamErasure() {
    doTest(new MemberDescriptor("f", PsiField.class));
  }

  @Test
  public void testTypeParamSubst() {
    doTest(new MemberDescriptor("f", PsiField.class));
  }

  @Test
  public void testTypeArgument() {
    doTest(new MemberDescriptor("f", PsiField.class));
  }

  @Test
  public void testGenericsInAbstractMethod() {
    doTest(new MemberDescriptor("method", PsiMethod.class, true));
  }

  public void _testReplaceDuplicatesInInheritors() {
    doTest(new MemberDescriptor("foo", PsiMethod.class, false));
  }

  @Test
  public void testGenericsInImplements() {
    doTest(false, new MemberDescriptor("I", PsiClass.class));
  }

  @Test
  public void testUpdateStaticRefs() {
    doTest(false, new MemberDescriptor("foo", PsiMethod.class));
  }

  @Test
  public void testRemoveOverrideFromPulledMethod() {
    doTest(false, new MemberDescriptor("foo", PsiMethod.class));
  }

  @Test
  public void testPreserveOverrideInPulledMethod() {
    doTest(false, new MemberDescriptor("foo", PsiMethod.class));
  }

  @Test
  public void testMergeInterfaces() {
    doTest(false, new MemberDescriptor("I", PsiClass.class));
  }

  @Test
  public void testTypeParamsConflictingNames() {
    doTest(false, new MemberDescriptor("foo", PsiMethod.class));
  }

  @Test
  public void testEscalateVisibility() {
    doTest(false, new MemberDescriptor("foo", PsiMethod.class));
  }

  @Test
  public void testPreserveOverride() {
    doTest(false, new MemberDescriptor("foo", PsiMethod.class));
  }

  @Test
  public void testImplementsList1() {
    doTest(false, new MemberDescriptor("I1", PsiClass.class));
  }

  @Test
  public void testImplementsList2() {
    doTest(false, new MemberDescriptor("I2", PsiClass.class));
  }

  @Test
  public void testImplementsList3() {
    doTest(false, new MemberDescriptor("I1", PsiClass.class), new MemberDescriptor("I2", PsiClass.class));
  }

  @Test
  public void testDocCommentInMethod() {
    doTest(false, new MemberDescriptor("foo", PsiMethod.class));
  }

  @Test
  public void testSupers() {
    doTest(false, new MemberDescriptor("bar", PsiMethod.class));
  }

  private void doTest(MemberDescriptor... membersToFind) {
    doTest(true, membersToFind);
  }

  private void doTest(final boolean checkMembersMovedCount, MemberDescriptor... membersToFind) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");
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
      JavaRefactoringListenerManager.getInstance(myFixture.getProject()).addMoveMembersListener(listener);
      final PullUpProcessor helper = new PullUpProcessor(sourceClass, targetClass, infos, new DocCommentPolicy(DocCommentPolicy.ASIS));
      helper.run();
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      JavaRefactoringListenerManager.getInstance(myFixture.getProject()).removeMoveMembersListener(listener);
      if (checkMembersMovedCount) {
        assertEquals(countMoved[0], membersToFind.length);
      }
      myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.groovy");
    });
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
      assertInstanceOf(GrMember.class, member);
      infos[i] = new MemberInfo(member, overrides, refList);
      infos[i].setToAbstract(membersToFind[i].myAbstract);
    }
    return infos;
  }
}