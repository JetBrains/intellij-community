/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.fixes.style;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.UnnecessaryFullyQualifiedNameInspection;
import org.intellij.lang.annotations.Language;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class UnnecessaryFullyQualifiedNameFixTest extends IGQuickFixesTestCase {

  public void testLeaveFQNamesInJavadoc() {
    doTest(
      "/**\n"                    +
      " * @see java.util.List\n" +
      " */\n"                    +
      "class X {"                +
      "  /**/java.util.List l;"  +
      "}",
      "import java.util.List;\n\n" +
      "/**\n"                      +
      " * @see java.util.List\n"   +
      " */\n"                      +
      "class X {"                  +
      "  List l;"                  +
      "}",
      JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_ALWAYS
    );
  }

  public void testReplaceFQNamesInJavadoc() {
    doTest(
      "/**\n"                    +
      " * @see java.util.List\n" +
      " */\n"                    +
      "class X {"                +
      "  /**/java.util.List l;"  +
      "}",
      "import java.util.List;\n\n" +
      "/**\n"                      +
      " * @see List\n"             +
      " */\n"                      +
      "class X {"                  +
      "  List l;"                  +
      "}",
      JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED
    );
  }

  public void testReplaceFQNamesInJavadoc2() {
    doTest(
      "/**\n"                    +
      " * @see java.util.List\n" +
      " */\n"                    +
      "class X {"                +
      "  /**/java.util.List l;"  +
      "}",
      "import java.util.List;\n\n" +
      "/**\n"                      +
      " * @see List\n"             +
      " */\n"                      +
      "class X {"                  +
      "  List l;"                  +
      "}",
      JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT
    );
  }

  private void doTest(@Language("JAVA") @NotNull @NonNls String before, @Language("JAVA") @NotNull @NonNls String after,
                      @MagicConstant(intValues = {
                        JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_ALWAYS,
                        JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED,
                        JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT
                      }) int classNamesInJavadoc) {
    final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    final JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);

    final int oldClassNamesInJavadoc = javaSettings.CLASS_NAMES_IN_JAVADOC;
    try {
      javaSettings.CLASS_NAMES_IN_JAVADOC = classNamesInJavadoc;
      doTest(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.replace.quickfix"), before, after);
    }
    finally {
      javaSettings.CLASS_NAMES_IN_JAVADOC = oldClassNamesInJavadoc;
    }
  }

  @Override
  protected BaseInspection getInspection() {
    return new UnnecessaryFullyQualifiedNameInspection();
  }
}
