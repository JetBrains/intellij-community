// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.immutable;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.LightProjectDescriptor;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrMethodImpl;

import java.util.Arrays;

public class ImmutableTransformationSupportTest extends LightGroovyTestCase {
  @Override
  @NotNull
  public final LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }
  
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.addFileToProject("classes.groovy", """
      @groovy.transform.Immutable(copyWith = true)
      class CopyWith {
        String stringProp
        Integer integerProp
      }
      """);
    myFixture.enableInspections(GrUnresolvedAccessInspection.class, GroovyAssignabilityCheckInspection.class);
  }

  public void testCopyWithNoProperties() {
    myFixture.configureByText("_.groovy", """
      @groovy.transform.Immutable(copyWith = true)
      class CopyWithNoProps {}
      new CopyWithNoProps().<warning descr="Cannot resolve symbol 'copyWith'">copyWith</warning>()
      """);
    myFixture.checkHighlighting();
  }

  public void testCopyWithExistsWithSingleParameter() {
    myFixture.addFileToProject("_.groovy", """
      @groovy.transform.Immutable(copyWith = true)
      class CopyWithExistingMethod {
        def copyWith(a) {}
      }
      """);
    PsiClass clazz = myFixture.findClass("CopyWithExistingMethod");
    PsiMethod[] methods = clazz.findMethodsByName("copyWith", false);
    assert DefaultGroovyMethods.size(methods) == 1;
    assert DefaultGroovyMethods.first(methods) instanceof GrMethodImpl;
  }

  public void testCopyWithResolveArguments() {
    myFixture.configureByText("_.groovy", """
      def usage(CopyWith cw) {
        cw.copyWith(st<caret>ringProp: 'hello')
      }
      """);
    PsiReference ref = myFixture.getFile().findReferenceAt(getEditor().getCaretModel().getOffset());
    assert ref != null;
    PsiElement resolved = ref.resolve();
    assert resolved instanceof GrField;
  }

  public void testCopyWithCheckArgumentTypes() {
    myFixture.configureByText("_.groovy", """
      def usage(CopyWith cw) {
        cw.copyWith(
          stringProp: 42,\s
          integerProp: <warning descr="Type of argument 'integerProp' can not be 'String'">'23'</warning>,\s
          unknownProp: new Object()
        )\s
      }
      """);
    myFixture.checkHighlighting();
  }

  public void testCopyWithCompleteProperties() {
    myFixture.configureByText("_.groovy", """
      def usage(CopyWith cw) {
        cw.copyWith(<caret>)
      }
      """);
    myFixture.complete(CompletionType.BASIC);
    assert myFixture.getLookupElementStrings().containsAll(Arrays.asList("stringProp", "integerProp"));
  }
}
