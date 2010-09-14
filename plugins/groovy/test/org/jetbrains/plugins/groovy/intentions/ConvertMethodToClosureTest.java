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

package org.jetbrains.plugins.groovy.intentions;

import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Maxim.Medvedev
 */
public class ConvertMethodToClosureTest extends GrIntentionTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "intentions/convertMethodToClosure/";
  }

  public void testMethodToClosure() throws Exception {
    doTest(GroovyIntentionsBundle.message("convert.method.to.closure.intention.name"), true);
  }

  public void testStaticMethodToClosure() throws Exception {
    doTest(GroovyIntentionsBundle.message("convert.method.to.closure.intention.name"), true);
  }

  public void testClosureToMethod() throws Exception {
    doTest(GroovyIntentionsBundle.message("convert.closure.to.method.intention.name"), true);
  }

  public void testClosureWithImplicitParameterToMethod() throws Exception {
    doTest(GroovyIntentionsBundle.message("convert.closure.to.method.intention.name"), true);
  }

  public void testClosureWithoutModifiersToMethod() {
    doTest(GroovyIntentionsBundle.message("convert.closure.to.method.intention.name"), true);
  }
}
