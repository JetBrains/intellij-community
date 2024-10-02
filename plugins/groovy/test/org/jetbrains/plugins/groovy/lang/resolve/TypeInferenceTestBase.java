// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.NestedContextKt;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * Created by Max Medvedev on 10/02/14
 */
public abstract class TypeInferenceTestBase extends GroovyResolveTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();

    myFixture.addClass("package java.math; public class BigDecimal extends Number implements Comparable<BigDecimal> {}");
    NestedContextKt.forbidNestedContext(getTestRootDisposable());
  }

  protected void doTest(String text, @Nullable String type) {
    PsiFile file = myFixture.configureByText("_.groovy", text);
    GrReferenceExpression ref =
      (GrReferenceExpression)file.findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
    PsiType actual = ref.getType();
    LightGroovyTestCase.assertType(type, actual);
  }

  protected void doExprTest(String text, @Nullable String expectedType) {
    GroovyFile file = (GroovyFile)myFixture.configureByText("_.groovy", text);
    GrStatement lastStatement = file.getStatements()[file.getStatements().length - 1];
    UsefulTestCase.assertInstanceOf(lastStatement, GrExpression.class);
    LightGroovyTestCase.assertType(expectedType, ((GrExpression)lastStatement).getType());
  }

  protected void doCSExprTest(String text, @Nullable String expectedType) {
    GroovyFile file = (GroovyFile)myFixture.configureByText("_.groovy",
                                                            """
                                                              @groovy.transform.CompileStatic
                                                              def m() {
                                                               \s""" + text + """
                                                              }
                                                              """);
    GrStatement[] statements = file.getMethods()[0].getBlock().getStatements();
    GrStatement lastStatement = statements[statements.length - 1];
    GrExpression expression = UsefulTestCase.assertInstanceOf(lastStatement, GrExpression.class);
    PsiType actual = expression.getType();
    LightGroovyTestCase.assertType(expectedType, actual);
  }

  @Override
  public final String getBasePath() {
    return basePath;
  }

  private final String basePath = TestUtils.getTestDataPath() + "resolve/inference/";
}
