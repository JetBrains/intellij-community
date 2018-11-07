// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.util.text.StringUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author gregsh
 */
@RunWith(JUnit4.class)
public abstract class LightPlatform4TestCase extends LightPlatformTestCase {
  @Rule
  public final TestName myTestName = new TestName();

  @Before
  @Override
  public void setUp() throws Exception {
    setName("test" + StringUtil.capitalize(myTestName.getMethodName()));
    super.setUp();
  }

  @After
  @Override
  public void tearDown() {
    EdtTestUtil.runInEdtAndWait(() -> super.tearDown());
  }
}
