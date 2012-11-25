/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang

import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil

/**
 * @author Sergey Evdokimov
 */
class GroovyNamedArgumentTest extends LightCodeInsightFixtureTestCase {

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
          assert na.checkType(PsiType.INT, context)
          assert na.checkType(PsiType.LONG, context)
          assert na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_INTEGER, context), context)

          assert !na.checkType(PsiType.BOOLEAN, (GroovyPsiElement)context)
          assert !na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, context), context)
        }
        else if (e.lookupString == "boolProperty") {
          assert na.checkType(PsiType.BOOLEAN, context)
          assert na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_BOOLEAN, context), context)

          // todo unkoment this
          //assert na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, context), context)
          //assert na.checkType(PsiType.INT, context)
          //assert na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_OBJECT, context), (GroovyPsiElement)context)
        }
        else if (e.lookupString == "stringField") {
          assert na.checkType(PsiType.INT, (GroovyPsiElement)context)
          assert na.checkType(PsiType.BOOLEAN, (GroovyPsiElement)context)
          assert na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, context), (GroovyPsiElement)context)
          assert na.checkType(TypesUtil.createType(CommonClassNames.JAVA_LANG_OBJECT, context), (GroovyPsiElement)context)
        }
      }
    }

    assert allLookupStrings == new HashSet(['intField', 'boolProperty', 'stringField'])
  }

}
