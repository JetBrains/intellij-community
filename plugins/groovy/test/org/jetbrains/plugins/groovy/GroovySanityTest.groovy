// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy

import com.intellij.openapi.application.PathManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.propertyBased.CheckHighlighterConsistency
import com.intellij.testFramework.propertyBased.MadTestingAction
import com.intellij.testFramework.propertyBased.MadTestingUtil
import groovy.transform.CompileStatic
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.PropertyChecker
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.enumConstant.GrEnumConstantImpl
import org.jetbrains.plugins.groovy.util.EdtRule
import org.jetbrains.plugins.groovy.util.FixtureRule
import org.jetbrains.plugins.groovy.util.Slow
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

import java.util.function.Function
import java.util.function.Supplier

import static com.intellij.testFramework.propertyBased.MadTestingUtil.actionsOnFileContents

@Slow
@CompileStatic
class GroovySanityTest {

  private static final FileFilter groovyFileFilter = new FileFilter() {
    @Override
    boolean accept(File pathname) {
      pathname.getName().endsWith(".groovy")
    }
  }

  private final FixtureRule myFixtureRule = new FixtureRule(GroovyProjectDescriptors.GROOVY_3_0, "")
  @Rule
  public final TestRule myRules = RuleChain.outerRule(myFixtureRule).around(new EdtRule())

  CodeInsightTestFixture getFixture() { myFixtureRule.fixture }

  private Supplier<MadTestingAction> actionsOnGroovyFiles(Function<PsiFile, Generator<? extends MadTestingAction>> fileActions) {
    actionsOnFileContents fixture, PathManager.homePath, groovyFileFilter, fileActions
  }

  @Test
  void 'incremental highlighter update'() {
    PropertyChecker.checkScenarios(actionsOnGroovyFiles(CheckHighlighterConsistency.randomEditsWithHighlighterChecks))
  }

  @Test
  void 'psi accessors'() {
    PropertyChecker.checkScenarios(actionsOnGroovyFiles(MadTestingUtil.randomEditsWithPsiAccessorChecks {
      (it.name == "getReference" || it.name == "getReferences") && it.declaringClass == GrLiteralImpl ||
      it.name == "getOrCreateInitializingClass" && it.declaringClass == GrEnumConstantImpl
    }))
  }
}
