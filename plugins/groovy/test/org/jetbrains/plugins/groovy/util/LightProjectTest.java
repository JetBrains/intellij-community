package org.jetbrains.plugins.groovy.util;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;

public abstract class LightProjectTest {
  public LightProjectTest(String testDataPath) {
    myTestName = new TestName();
    myFixtureRule = new FixtureRule(getProjectDescriptor(), testDataPath);
    myRules = RuleChain.outerRule(myTestName).around(myFixtureRule).around(new EdtRule());
  }

  public LightProjectTest() {
    this("");
  }

  public abstract LightProjectDescriptor getProjectDescriptor();

  public String getTestName() {
    return myTestName.getMethodName();
  }

  @NotNull
  public final JavaCodeInsightTestFixture getFixture() {
    return myFixtureRule.getFixture();
  }

  private final TestName myTestName;
  private final FixtureRule myFixtureRule;

  @Rule public final TestRule myRules;
}
