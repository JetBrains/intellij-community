/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

/**
 * @author peter
 */
public class GroovyDslTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return "/svnPlugins/groovy/testdata/groovy/dsl";
  }

  public void testCompleteMethod() throws Throwable { doTest(); }
  public void testCompleteProperty() throws Throwable { doTest(); }

  public void testCompleteClassMethod() throws Throwable {
    myFixture.addFileToProject("stringEnhancer.gdsl", "enhanceClass(className:\"java.lang.String\") { method name:\"zzz\", type:\"void\", params:[:] }");
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + "_after.groovy");
  }

  private void doTest() throws Throwable {
    myFixture.testCompletion(getTestName(false) + ".gdsl", getTestName(false) + "_after.gdsl");
  }
}
