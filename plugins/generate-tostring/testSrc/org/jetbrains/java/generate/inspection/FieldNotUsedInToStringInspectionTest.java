/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
public class FieldNotUsedInToStringInspectionTest extends LightCodeInsightFixtureTestCase {

  public void testBasic() {
    doTest("class X {" +
           "  private int <warning descr=\"Field 'i' is not used in 'toString()' method\">i</warning> = 0;" +
           "  public String toString() {" +
           "    return null;" +
           "  }" +
           "}");
  }

  public void testGetterUsed() {
    doTest(" class ToStringTest3 {" +
           "" +
           "    int number;" +
           "" +
           "    public int getNumber() {" +
           "        return number;" +
           "    }" +
           "" +
           "    @Override" +
           "    public String toString() {" +
           "        final StringBuilder sb = new StringBuilder();" +
           "        sb.append(\"ToStringTest3\");" +
           "        sb.append(\"{number=\").append(getNumber());" +
           "        sb.append('}');" +
           "        return sb.toString();" +
           "    }" +
           "}");
  }

  public void testReflectionUsed() {
    myFixture.addClass("package java.util;" +
                       "public class Objects {" +
                       "  public static String toString(Object object) {" +
                       "    return null;" +
                       "  }" +
                       "}");
    doTest("import java.util.Objects;" +
           "class X {" +
           "  private int i = 0;" +
           "  " +
           "  public String toString() {" +
           "    return Objects.toString(this);" +
           "  }" +
           "}");
  }

  private void doTest(@NonNls String text) {
    myFixture.configureByText("X.java", text);
    myFixture.enableInspections(new FieldNotUsedInToStringInspection());
    myFixture.testHighlighting(true, false, false);
  }
}
