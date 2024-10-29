// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public class GroovyNamedArgumentTest extends LightJavaCodeInsightFixtureTestCase {
  public void testNamedArgumentsFromJavaClass() {
    myFixture.addClass("""
      class JavaClass {
        public int intField;
        private String stringField;
    
        public static String staticField;
    
        private int boolProperty;
    
        public void setBoolProperty(boolean b) {
          boolProperty = b ? 1 : 0;
        }
    
        public void getBoolProperty() {
          return boolProperty == 1;
        }
      }
    """);

    myFixture.configureByText("a.groovy", "new JavaClass(<caret>)");
    int caretOffset = myFixture.getCaretOffset();
    LookupElement[] lookUps = myFixture.completeBasic();
    assertNotNull(lookUps);
    GroovyPsiElement context = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(caretOffset), GroovyPsiElement.class);
    HashSet<String> allLookupStrings = new HashSet<>();
    for (LookupElement e : lookUps) {
      if (e.getObject() instanceof NamedArgumentDescriptor na) {
        DefaultGroovyMethods.leftShift(allLookupStrings, e.getLookupString());
        if (e.getLookupString().equals("intField")) {
          assert na.checkType(PsiTypes.intType(), context);
          assert na.checkType(PsiTypes.longType(), context);
          assert na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_INTEGER, context), context);

          assert !na.checkType(PsiTypes.booleanType(), context);
          assert !na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, context), context);
        }
        else if (e.getLookupString().equals("boolProperty")) {
          assert na.checkType(PsiTypes.booleanType(), context);
          assert na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_BOOLEAN, context), context);

          // todo unkoment this
          //assert na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, context), context)
          //assert na.checkType(PsiTypes.intType(, context)
          //assert na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_OBJECT, context), (GroovyPsiElement)context)
        }
        else if (e.getLookupString().equals("stringField")) {
          assert na.checkType(PsiTypes.intType(), context);
          assert na.checkType(PsiTypes.booleanType(), context);
          assert na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, context), context);
          assert na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_OBJECT, context), context);
        }
      }
    }


    assertEquals(allLookupStrings, new HashSet<>(new ArrayList<>(Arrays.asList("intField", "boolProperty", "stringField", "staticField"))));
  }
}
