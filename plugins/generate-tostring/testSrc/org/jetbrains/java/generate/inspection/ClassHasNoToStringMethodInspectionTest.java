/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
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
package org.jetbrains.java.generate.inspection;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;

/**
 * @author Bas Leijdekkers
 */
public class ClassHasNoToStringMethodInspectionTest extends LightCodeInsightFixtureTestCase {

  public void testBasic() {
    doTest("class <warning descr=\"Class 'X' does not override 'toString()' method\">X</warning> {" +
           "  private int i = 0;" +
           "}");
  }

  public void testDoNotWarnOnInnerClass() {
    doTest("class X {" +
           "  class Inner {" +
           "    private int i = 0;" +
           "  }" +
           "}");
  }

  private void doTest(@NonNls String text) {
    myFixture.configureByText("X.java", text);
    final ClassHasNoToStringMethodInspection inspection = new ClassHasNoToStringMethodInspection();
    inspection.excludeInnerClasses = true;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, false, false);
  }
}
