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
package com.siyeh.ig;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public abstract class LightInspectionTestCase extends LightCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    for (String environmentClass : getEnvironmentClasses()) {
      myFixture.addClass(environmentClass);
    }
    myFixture.enableInspections(getInspection());
  }

  protected abstract LocalInspectionTool getInspection();

  @NonNls
  protected String[] getEnvironmentClasses() {
    return new String[]{};
  }

  protected void addEnvironmentClass(@Language("JAVA") @NotNull @NonNls String classText) {
    myFixture.addClass(classText);
  }

  protected final void doTest(@Language("JAVA") @NotNull @NonNls String classText) {
    @NonNls final StringBuilder newText = new StringBuilder();
    int start = 0;
    int end = classText.indexOf("/*");
    while (end >= 0) {
      newText.append(classText, start, end);
      start = end + 2;
      end = classText.indexOf("*/", end);
      if (end < 0) {
        throw new IllegalArgumentException("invalid class text");
      }
      final String warning = classText.substring(start, end);
      if (warning.isEmpty()) {
        newText.append("</warning>");
      } else {
        newText.append("<warning descr=\"").append(warning).append("\">");
      }
      start = end + 2;
      end = classText.indexOf("/*", end + 1);
    }
    newText.append(classText, start, classText.length());
    myFixture.configureByText("X.java", newText.toString());
    myFixture.testHighlighting(true, false, false);
  }
}
