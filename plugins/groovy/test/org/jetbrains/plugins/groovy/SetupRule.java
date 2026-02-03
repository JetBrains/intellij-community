package org.jetbrains.plugins.groovy;

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import static org.jetbrains.plugins.groovy.GroovyProjectDescriptors.GROOVY_LATEST_REAL_JDK;

public class SetupRule implements TestRule {
  @Override
  public Statement apply(final Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        getTestCase().setUp();
        try {
          EdtTestUtil.runInEdtAndWait(() -> base.evaluate());
        }
        finally {
          getTestCase().tearDown();
        }
      }
    };
  }

  public final LightGroovyTestCase getTestCase() {
    return testCase;
  }

  private final LightGroovyTestCase testCase = new LightGroovyTestCase() {
    @Override
    public @NotNull LightProjectDescriptor getProjectDescriptor() {
      return projectDescriptor;
    }

    public void setProjectDescriptor(LightProjectDescriptor projectDescriptor) {
      this.projectDescriptor = projectDescriptor;
    }

    private LightProjectDescriptor projectDescriptor = GROOVY_LATEST_REAL_JDK;
  };
}
