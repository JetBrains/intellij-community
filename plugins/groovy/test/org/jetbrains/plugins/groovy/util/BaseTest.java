package org.jetbrains.plugins.groovy.util;

import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public interface BaseTest {

  @NotNull
  CodeInsightTestFixture getFixture();

  default GroovyFile configureByText(@NotNull String text) {
    return (GroovyFile)getFixture().configureByText("_.groovy", text);
  }

  default GrExpression configureByExpression(@NotNull String text) {
    return (GrExpression)ArrayUtil.getLastElement(configureByText(text).getStatements());
  }
}
