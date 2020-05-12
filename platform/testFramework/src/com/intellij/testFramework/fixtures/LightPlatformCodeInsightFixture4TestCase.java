// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.TestRunnerUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ThrowableRunnable;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.model.Statement;

/**
 * @author gregsh
 */
@RunWith(JUnit4.class)
public abstract class LightPlatformCodeInsightFixture4TestCase extends BasePlatformTestCase {
  @Rule
  public TestRule rule = (base, description) -> new Statement() {
    /**
     * Mimics the method {@link UsefulTestCase#runBare()}.
     *
     * @throws Throwable if exception is occurred during the test execution
     */
    @Override
    public void evaluate() throws Throwable {
      if (!shouldRunTest()) return;

      String name = description.getMethodName();
      setName(name.startsWith("test") ? name : "test" + StringUtil.capitalize(name));
      ThrowableRunnable<Throwable> runnable = () -> {
        setUp();
        try {
          base.evaluate();
        }
        finally {
          tearDown();
        }
      };
      if (runInDispatchThread()) {
        TestRunnerUtil.replaceIdeEventQueueSafely();
        EdtTestUtil.runInEdtAndWait(runnable);
      }
      else {
        runnable.run();
      }
    }
  };
}
