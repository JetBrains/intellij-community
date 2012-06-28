/*
 * Copyright 2000-2007 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.completion;

import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author ven
 */
public class ReferenceCompletionTest extends CompletionTestBase {

  public void testEscapedReference() throws Throwable { doTest(); }
  public void testGrvy1021() throws Throwable { doTest(); }
  public void testGrvy1021a() throws Throwable { doTest(); }
  public void testGrvy1021b() throws Throwable { doTest(); }
  public void testGrvy1156() throws Throwable { doTest(); }
  public void testGrvy117() throws Throwable { doTest(); }
  public void testGrvy1272() throws Throwable { doTest(); }
  public void testGRVY1316() throws Throwable { doTest(); }
  public void testGrvy194() throws Throwable { doTest(); }
  public void testGrvy194_1() throws Throwable { doTest(); }
  public void testGRVY223() throws Throwable { doTest(); }
  public void testGrvy487() throws Throwable { doTest(); }
  public void testGrvy491() throws Throwable { doTest(); }
  public void testGrvy76() throws Throwable { doTest(); }
  public void testGrvy959() throws Throwable { doTest(); }
  public void testOnDemand() throws Throwable { doTest(); }
  public void testStaticMethod() throws Throwable { doTest(); }
  public void testThrowVariable() throws Throwable { doTest(); }
  public void testTupleCompl1() throws Throwable { doTest(); }
  public void testTupleLongListAssign() throws Throwable { doTest(); }
  public void testTupleObjCompl() throws Throwable { doTest(); }
  public void testTupleShortListAss() throws Throwable { doTest(); }
  public void testTupleTypedCompl() throws Throwable { doTest(); }
  public void testType() throws Throwable { doTest(); }
  public void testUntyped() throws Throwable { doTest(); }

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/oldCompletion/reference";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addJdk(TestUtils.getMockJdkHome());
  }

}
