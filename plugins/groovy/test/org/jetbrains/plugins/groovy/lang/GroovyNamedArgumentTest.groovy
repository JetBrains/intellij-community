// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang

import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiTypes
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil 

class GroovyNamedArgumentTest extends LightJavaCodeInsightFixtureTestCase {

  void testNamedArgumentsFromJavaClass() {
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
""")

    myFixture.configureByText("a.groovy", "new JavaClass(<caret>)")

    def caretOffset = myFixture.getCaretOffset()

    def lookUps = myFixture.completeBasic()

    assert lookUps != null

    def context = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(caretOffset), GroovyPsiElement)

    def allLookupStrings = new HashSet()

    for (def e : lookUps) {
      if (e.object instanceof NamedArgumentDescriptor) {
        NamedArgumentDescriptor na = e.object

        allLookupStrings << e.lookupString

        if (e.lookupString == "intField") {
          assert na.checkType(PsiTypes.intType(), context)
          assert na.checkType(PsiTypes.longType(), context)
          assert na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_INTEGER, context), context)

          assert !na.checkType(PsiTypes.booleanType(), (GroovyPsiElement)context)
          assert !na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, context), context)
        }
        else if (e.lookupString == "boolProperty") {
          assert na.checkType(PsiTypes.booleanType(), context)
          assert na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_BOOLEAN, context), context)

          // todo unkoment this
          //assert na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, context), context)
          //assert na.checkType(PsiTypes.intType(, context)
          //assert na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_OBJECT, context), (GroovyPsiElement)context)
        }
        else if (e.lookupString == "stringField") {
          assert na.checkType(PsiTypes.intType(), (GroovyPsiElement)context)
          assert na.checkType(PsiTypes.booleanType(), (GroovyPsiElement)context)
          assert na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, context), (GroovyPsiElement)context)
          assert na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_OBJECT, context), (GroovyPsiElement)context)
        }
      }
    }

    assert allLookupStrings == new HashSet(['intField', 'boolProperty', 'stringField', 'staticField'])
  }

}
