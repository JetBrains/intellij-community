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

  public void testEscapedReference() { doTest(); }
  public void testGrvy1021() { doTest(); }
  public void testGrvy1021a() { doTest(); }
  public void testGrvy1021b() { doTest(); }
  public void testGrvy1156() { doTest(); }
  public void testGrvy117() { doTest(); }
  public void testGrvy1272() { doTest(); }
  public void testGRVY1316() { doTest(); }
  public void testGrvy194() { doTest(); }
  public void testGrvy194_1() { doTest(); }
  public void testGRVY223() { doTest(); }
  public void testGrvy487() { doTest(); }
  public void testGrvy491() { doTest(); }
  public void testGrvy76() { doTest(); }
  public void testGrvy959() { doTest(); }
  public void testOnDemand() { doTest(); }
  public void testStaticMethod() { doTest(); }
  public void testThrowVariable() { doTest(); }
  public void testTupleCompl1() { doTest(); }
  public void testTupleLongListAssign() { doTest(); }
  public void testTupleObjCompl() { doTest(); }
  public void testTupleShortListAss() { doTest(); }
  public void testTupleTypedCompl() { doTest(); }
  public void testType() { doTest(); }
  public void testUntyped() { doTest(); }

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/oldCompletion/reference";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addJdk(TestUtils.getMockJdkHome());
  }

}
