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

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtil;
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

  protected abstract InspectionProfileEntry getInspection();

  @NonNls
  protected String[] getEnvironmentClasses() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  protected void addEnvironmentClass(@Language("JAVA") @NotNull @NonNls String classText) {
    myFixture.addClass(classText);
  }

  protected final void doStatementTest(@Language(value="JAVA", prefix="class X { void m() {", suffix="}}") @NotNull @NonNls String statementText) {
    doTest("class X { void m() {" + statementText + "}}");
  }

  protected final void doMemberTest(@Language(value="JAVA", prefix="class X {", suffix="}") @NotNull @NonNls String memberText) {
    doTest("class X {" + memberText + "}");
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

  @Override
  protected String getBasePath() {
    final Class<? extends InspectionProfileEntry> inspectionClass = getInspection().getClass();
    final String className = inspectionClass.getName();
    final String[] words = className.split("\\.");
    @NonNls final StringBuilder basePath = new StringBuilder("/plugins/InspectionGadgets/test/");
    final int lastWordIndex = words.length - 1;
    for (int i = 0; i < lastWordIndex; i++) {
      final String word = words[i];
      if (word.equals("ig")) {
        basePath.append("igtest");
      }
      else {
        basePath.append(word);
      }
      basePath.append('/');
    }
    String lastWord = words[lastWordIndex];
    if (lastWord.endsWith("Inspection")) {
      lastWord = lastWord.substring(0, lastWord.length() - 10);
    }
    final int length = lastWord.length();
    for (int i = 0; i < length; i++) {
      final char ch = lastWord.charAt(i);
      if (Character.isUpperCase(ch)) {
        if (i != 0) {
          basePath.append('_');
        }
        basePath.append(Character.toLowerCase(ch));
      }
      else {
        basePath.append(ch);
      }
    }
    return basePath.toString();
  }

  protected final void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.testHighlighting(true, false, false);
  }
}
