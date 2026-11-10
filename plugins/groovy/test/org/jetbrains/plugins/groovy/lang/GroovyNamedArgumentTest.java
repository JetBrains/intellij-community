// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class GroovyNamedArgumentTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testNamedArgumentsFromJavaClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    int caretOffset = ReadAction.computeBlocking(() -> myFixture.getCaretOffset());
    LookupElement[] lookUps = myFixture.completeBasic();
    assertNotNull(lookUps);
    ReadAction.runBlocking(() -> {
      GroovyPsiElement context = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(caretOffset), GroovyPsiElement.class);
      HashSet<String> allLookupStrings = new HashSet<>();
      for (LookupElement e : lookUps) {
        if (e.getObject() instanceof NamedArgumentDescriptor na) {
          DefaultGroovyMethods.leftShift(allLookupStrings, e.getLookupString());
          if (e.getLookupString().equals("intField")) {
            assertTrue(na.checkType(PsiTypes.intType(), context));
            assertTrue(na.checkType(PsiTypes.longType(), context));
            assertTrue(na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_INTEGER, context), context));

            assertFalse(na.checkType(PsiTypes.booleanType(), context));
            assertFalse(na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, context), context));
          }
          else if (e.getLookupString().equals("boolProperty")) {
            assertTrue(na.checkType(PsiTypes.booleanType(), context));
            assertTrue(na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_BOOLEAN, context), context));

            // todo unkoment this
            //assert na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, context), context)
            //assert na.checkType(PsiTypes.intType(, context)
            //assert na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_OBJECT, context), (GroovyPsiElement)context)
          }
          else if (e.getLookupString().equals("stringField")) {
            assertTrue(na.checkType(PsiTypes.intType(), context));
            assertTrue(na.checkType(PsiTypes.booleanType(), context));
            assertTrue(na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, context), context));
            assertTrue(na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_OBJECT, context), context));
          }
        }
      }

      assertEquals(allLookupStrings, new HashSet<>(new ArrayList<>(Arrays.asList("intField", "boolProperty", "stringField", "staticField"))));
    });
  }
}
