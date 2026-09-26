// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.propertyBased.CheckHighlighterConsistency;
import com.intellij.testFramework.propertyBased.MadTestingAction;
import com.intellij.testFramework.propertyBased.MadTestingUtil;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.PropertyChecker;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.DfaCacheConsistencyKt;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.enumConstant.GrEnumConstantImpl;
import org.jetbrains.plugins.groovy.util.EdtRule;
import org.jetbrains.plugins.groovy.util.FixtureRule;
import org.jetbrains.plugins.groovy.util.Slow;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.io.FileFilter;
import java.util.function.Function;
import java.util.function.Supplier;

@Slow
public class GroovySanityTest {
  public CodeInsightTestFixture getFixture() { return myFixtureRule.getFixture(); }

  private Supplier<MadTestingAction> actionsOnGroovyFiles(Function<PsiFile, Generator<? extends MadTestingAction>> fileActions) {
    return MadTestingUtil.actionsOnFileContents(getFixture(), PathManager.getHomePath(), groovyFileFilter, fileActions);
  }

  @Test
  public void incremental_highlighter_update() {
    PropertyChecker.checkScenarios(actionsOnGroovyFiles(CheckHighlighterConsistency.randomEditsWithHighlighterChecks));
  }

  @Test
  public void inc() {
    PsiFile file = getFixture().configureByText("_.groovy", """
    
          /**1*/
          /**2*/
          a={}
          /**3*/
    """);
    final Document document = getFixture().getDocument(file);
    WriteCommandAction.runWriteCommandAction(getFixture().getProject(), () -> {
        document.deleteString(8, 15);
        document.insertString(0, "[");
      });
  }

  @Test
  public void psi_accessors() {
    RecursionManager.disableMissedCacheAssertions(getFixture().getTestRootDisposable());
    DfaCacheConsistencyKt.allowCacheInconsistency(getFixture().getTestRootDisposable());
    PropertyChecker.checkScenarios(actionsOnGroovyFiles(MadTestingUtil.randomEditsWithPsiAccessorChecks(method -> {
      return method.getName().equals("getOrCreateInitializingClass") && method.getDeclaringClass().equals(GrEnumConstantImpl.class);
    })));
  }

  private static final FileFilter groovyFileFilter = pathname -> pathname.getName().endsWith(".groovy");
  private final FixtureRule myFixtureRule = new FixtureRule(GroovyProjectDescriptors.GROOVY_3_0, "");
  @Rule public final TestRule myRules = RuleChain.outerRule(myFixtureRule).around(new EdtRule());
}
