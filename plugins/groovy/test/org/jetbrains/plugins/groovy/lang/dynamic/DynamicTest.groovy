// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.dynamic

import com.intellij.psi.PsiType
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicManager
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ParamInfo
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DClassElement
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DRootElement
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyNamesUtil
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.util.BaseTest
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.junit.Test

import static org.junit.Assert.assertNotNull

@CompileStatic
class DynamicTest extends GroovyLatestTest implements BaseTest {

  DynamicTest() {
    super("dynamic")
  }

  @Test
  void method() {
    fixture.enableInspections(new GrUnresolvedAccessInspection())
    fixture.configureByFile(testName.capitalize() + ".groovy")
    fixture.launchAction(fixture.findSingleIntention("Add dynamic method"))

    GrReferenceExpression referenceExpression = elementUnderCaret(GrReferenceExpression.class)

    final PsiType[] psiTypes = PsiUtil.getArgumentTypes(referenceExpression, false)
    final String[] methodArgumentsNames = GroovyNamesUtil.getMethodArgumentsNames(getProject(), psiTypes)
    final List<ParamInfo> pairs = QuickfixUtil.swapArgumentsAndTypes(methodArgumentsNames, psiTypes)

    assertNotNull(getDClassElement().getMethod(referenceExpression.getReferenceName(), QuickfixUtil.getArgumentsTypes(pairs)))
  }

  @Test
  void property() {
    fixture.enableInspections(new GrUnresolvedAccessInspection())
    fixture.configureByFile(testName.capitalize() + ".groovy")
    fixture.launchAction(fixture.findSingleIntention("Add dynamic property"))
    GrReferenceExpression referenceExpression = elementUnderCaret(GrReferenceExpression.class)
    assertNotNull(getDClassElement().getPropertyByName(referenceExpression.getReferenceName()))
  }

  @NotNull
  private DClassElement getDClassElement() {
    final DRootElement rootElement = DynamicManager.getInstance(getProject()).getRootElement()
    final DClassElement classElement = rootElement.getClassElement(testName.capitalize())
    assertNotNull(classElement)
    return classElement
  }
}
