/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.formatter;

import com.intellij.application.options.IndentOptionsEditor;
import com.intellij.application.options.SmartIndentOptionsEditor;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;

import javax.swing.*;

/**
 * @author Rustam Vishnyakov
 */
public class GroovyLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  @NotNull
  @Override
  public Language getLanguage() {
    return GroovyFileType.GROOVY_LANGUAGE;
  }

  @Override
  public void customizeSettings(@NotNull CodeStyleSettingsCustomizable consumer,
                                @NotNull SettingsType settingsType) {
    if (settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS) {
      consumer.showAllStandardOptions();
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "USE_FLYING_GEESE_BRACES", "Use flying geese braces",
                                CodeStyleSettingsCustomizable.WRAPPING_BRACES);
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "ALIGN_MULTILINE_LIST_OR_MAP", "Align when multiple", "List and map literals");
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "ALIGN_NAMED_ARGS_IN_MAP", "Align named arguments", "List and map literals");
      return;
    }
    if (settingsType == SettingsType.SPACING_SETTINGS) {
      consumer.showStandardOptions("INSERT_FIRST_SPACE_IN_LINE",
                                   "SPACE_AROUND_ASSIGNMENT_OPERATORS",
                                   "SPACE_AROUND_LOGICAL_OPERATORS",
                                   "SPACE_AROUND_EQUALITY_OPERATORS",
                                   "SPACE_AROUND_RELATIONAL_OPERATORS",
                                   "SPACE_AROUND_BITWISE_OPERATORS",
                                   "SPACE_AROUND_ADDITIVE_OPERATORS",
                                   "SPACE_AROUND_MULTIPLICATIVE_OPERATORS",
                                   "SPACE_AROUND_SHIFT_OPERATORS",
                                   "SPACE_AROUND_UNARY_OPERATOR",
                                   "SPACE_AFTER_COMMA",
                                   "SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS",
                                   "SPACE_BEFORE_COMMA",
                                   "SPACE_AFTER_SEMICOLON",
                                   "SPACE_BEFORE_SEMICOLON",
                                   "SPACE_WITHIN_PARENTHESES",
                                   "SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES",
                                   "SPACE_WITHIN_METHOD_CALL_PARENTHESES",
                                   "SPACE_WITHIN_METHOD_PARENTHESES",
                                   "SPACE_WITHIN_IF_PARENTHESES",
                                   "SPACE_WITHIN_WHILE_PARENTHESES",
                                   "SPACE_WITHIN_FOR_PARENTHESES",
//                                   "SPACE_WITHIN_TRY_PARENTHESES",
                                   "SPACE_WITHIN_CATCH_PARENTHESES",
                                   "SPACE_WITHIN_SWITCH_PARENTHESES",
                                   "SPACE_WITHIN_SYNCHRONIZED_PARENTHESES",
                                   "SPACE_WITHIN_CAST_PARENTHESES",
                                   "SPACE_WITHIN_BRACKETS",
                                   "SPACE_WITHIN_BRACES",
//                                   "SPACE_WITHIN_ARRAY_INITIALIZER_BRACES",
                                   "SPACE_AFTER_TYPE_CAST",
                                   "SPACE_BEFORE_METHOD_CALL_PARENTHESES",
                                   "SPACE_BEFORE_METHOD_PARENTHESES",
                                   "SPACE_BEFORE_IF_PARENTHESES",
                                   "SPACE_BEFORE_WHILE_PARENTHESES",
                                   "SPACE_BEFORE_FOR_PARENTHESES",
//                                   "SPACE_BEFORE_TRY_PARENTHESES",
                                   "SPACE_BEFORE_CATCH_PARENTHESES",
                                   "SPACE_BEFORE_SWITCH_PARENTHESES",
                                   "SPACE_BEFORE_SYNCHRONIZED_PARENTHESES",
                                   "SPACE_BEFORE_CLASS_LBRACE",
                                   "SPACE_BEFORE_METHOD_LBRACE",
                                   "SPACE_BEFORE_IF_LBRACE",
                                   "SPACE_BEFORE_ELSE_LBRACE",
                                   "SPACE_BEFORE_WHILE_LBRACE",
                                   "SPACE_BEFORE_FOR_LBRACE",
//                                   "SPACE_BEFORE_DO_LBRACE",
                                   "SPACE_BEFORE_SWITCH_LBRACE",
                                   "SPACE_BEFORE_TRY_LBRACE",
                                   "SPACE_BEFORE_CATCH_LBRACE",
                                   "SPACE_BEFORE_FINALLY_LBRACE",
                                   "SPACE_BEFORE_SYNCHRONIZED_LBRACE",
//                                   "SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE",
//                                   "SPACE_BEFORE_ANNOTATION_ARRAY_INITIALIZER_LBRACE",
                                   "SPACE_BEFORE_ELSE_KEYWORD",
//                                   "SPACE_BEFORE_WHILE_KEYWORD",
                                   "SPACE_BEFORE_CATCH_KEYWORD",
                                   "SPACE_BEFORE_FINALLY_KEYWORD",
                                   "SPACE_BEFORE_QUEST",
                                   "SPACE_AFTER_QUEST",
                                   "SPACE_BEFORE_COLON",
                                   "SPACE_AFTER_COLON",
                                   "SPACE_BEFORE_TYPE_PARAMETER_LIST",
                                   "SPACE_BEFORE_ANOTATION_PARAMETER_LIST",
                                   "SPACE_WITHIN_ANNOTATION_PARENTHESES"
      );
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "SPACE_IN_NAMED_ARGUMENT", "In named argument after ':'", CodeStyleSettingsCustomizable.SPACES_OTHER);
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "SPACE_WITHIN_LIST_OR_MAP", "List and maps literals", CodeStyleSettingsCustomizable.SPACES_WITHIN);
      return;
    }
    consumer.showAllStandardOptions();
  }

  @Override
  public CommonCodeStyleSettings getDefaultCommonSettings() {
    CommonCodeStyleSettings defaultSettings = new CommonCodeStyleSettings(GroovyFileType.GROOVY_LANGUAGE);
    defaultSettings.initIndentOptions();
    return defaultSettings;
  }

  @Override
  public boolean usesSharedPreview() {
    return false;
  }

  @Override
  public String getCodeSample(@NotNull SettingsType settingsType) {
    switch (settingsType) {
      case INDENT_SETTINGS: return INDENT_OPTIONS_SAMPLE;
      case SPACING_SETTINGS: return SPACING_SAMPLE;
      case WRAPPING_AND_BRACES_SETTINGS: return WRAPPING_CODE_SAMPLE;
      case BLANK_LINES_SETTINGS: return BLANK_LINE_SAMPLE;
      default:
        return "";
    }
  }

  @Override
  public IndentOptionsEditor getIndentOptionsEditor() {
    return new SmartIndentOptionsEditor() {
      private JTextField myLabelIndent;
      private JLabel myLabelIndentLabel;

      private JCheckBox myLabelIndentAbsolute;

      protected void addComponents() {
        super.addComponents();

        myLabelIndent = new JTextField(4);
        add(myLabelIndentLabel = new JLabel(ApplicationBundle.message("editbox.indent.label.indent")), myLabelIndent);

        myLabelIndentAbsolute = new JCheckBox(ApplicationBundle.message("checkbox.indent.absolute.label.indent"));
        add(myLabelIndentAbsolute, true);
      }

      public boolean isModified(final CodeStyleSettings settings, final CommonCodeStyleSettings.IndentOptions options) {
        boolean isModified = super.isModified(settings, options);

        isModified |= isFieldModified(myLabelIndent, options.LABEL_INDENT_SIZE);
        isModified |= isFieldModified(myLabelIndentAbsolute, options.LABEL_INDENT_ABSOLUTE);

        return isModified;
      }

      public void apply(final CodeStyleSettings settings, final CommonCodeStyleSettings.IndentOptions options) {
        super.apply(settings, options);
        options.LABEL_INDENT_SIZE = getFieldValue(myLabelIndent, Integer.MIN_VALUE, options.LABEL_INDENT_SIZE);
        options.LABEL_INDENT_ABSOLUTE = myLabelIndentAbsolute.isSelected();
      }

      public void reset(@NotNull final CodeStyleSettings settings, @NotNull final CommonCodeStyleSettings.IndentOptions options) {
        super.reset(settings, options);
        myLabelIndent.setText(Integer.toString(options.LABEL_INDENT_SIZE));
        myLabelIndentAbsolute.setSelected(options.LABEL_INDENT_ABSOLUTE);
      }

      public void setEnabled(final boolean enabled) {
        super.setEnabled(enabled);
        myLabelIndent.setEnabled(enabled);
        myLabelIndentLabel.setEnabled(enabled);
        myLabelIndentAbsolute.setEnabled(enabled);
      }
    };
  }
  
  

  private final static String INDENT_OPTIONS_SAMPLE =
    /*
    "topLevelLabel:\n" +
    "foo(42)\n" +
    */
    "def foo(int arg) {\n" +
    "  label1:\n" +
    "  for (i in 1..10) {\n" +
    "    label2:\n" +
    "    foo(i)\n" +
    "  }\n" +
    "  return Math.max(arg,\n" +
    "      0)\n" +
    "}";
  
  private final static String SPACING_SAMPLE =
    "class Foo {\n" +
    "  @Annotation(param=\"foo\")\n"+
    "  @Ann([1, 2])\n" +
    "  public static <T1, T2> void foo(int x, int y) {\n" +
    "    for (int i = 0; i < x; i++) {\n" +
    "      y += (y ^ 0x123) << 2\n" +
    "    }\n" +
    "    int j = 0\n" +
    "    while (j < 10) {\n" +
    "      try {\n" +
    "        if (0 < x && x < 10) {\n" +
    "          while (x != y) {\n" +
    "            x = f(x * 3 + 5)\n" +
    "          }\n" +
    "        } else {\n" +
    "          synchronized (this) {\n" +
    "            switch (e.getCode()) {\n" +
    "            //...\n" +
    "            }\n" +
    "          }\n" +
    "        }\n" +
    "      } catch (MyException e) {\n" +
    "        logError(method: \"foo\", exception: e)\n" +
    "      } finally {\n" +
    "        int[] arr = (int[]) g(y)\n" +
    "        x = y >= 0 ? arr[y] : -1\n" +
    "        y = [1, 2, 3] ?: 4\n" +
    "      }\n" +
    "    }\n" +
    "  }\n" +
    "\n" +
    "}";
  private static final String WRAPPING_CODE_SAMPLE =
    "/*\n" +
    " * This is a sample file.\n" +
    " */\n" +
    "\n" +
    "public class ThisIsASampleClass extends C1 implements I1, I2, I3, I4, I5 {\n" +
    "  private int f1 = 1\n" +
    "  private String field2 = \"\"\n" +
    "  public void foo1(int i1, int i2, int i3, int i4, int i5, int i6, int i7) {}\n" +
    "  public static void longerMethod() throws Exception1, Exception2, Exception3 {\n" +
    "// todo something\n" +
    "    int\n" +
    "i = 0\n" +
    "    int var1 = 1; int var2 = 2\n" +
    "    foo1(0x0051, 0x0052, 0x0053, 0x0054, 0x0055, 0x0056, 0x0057)\n" +
    "    int x = (3 + 4 + 5 + 6) * (7 + 8 + 9 + 10) * (11 + 12 + 13 + 14 + 0xFFFFFFFF)\n" +
    "    String s1, s2, s3\n" +
    "    s1 = s2 = s3 = \"012345678901456\"\n" +
    "    assert i + j + k + l + n+ m <= 2 : \"assert description\"" +
    "    int y = 2 > 3 ? 7 + 8 + 9 : 11 + 12 + 13\n" +
    "    super.getFoo().foo().getBar().bar()\n" +
    "\n" +
    "    label: " +
    "    if (2 < 3) return else if (2 > 3) return else return\n" +
    "    for (int i = 0; i < 0xFFFFFF; i += 2) System.out.println(i)\n" +
    "    print([\n" +
    "       l1: expr1,\n" +
    "       label2: expr2\n" +
    "    ])\n" +
    "    while (x < 50000) x++\n" +
    "    switch (a) {\n" +
    "    case 0:\n" +
    "      doCase0()\n" +
    "      break\n" +
    "    default:\n" +
    "      doDefault()\n" +
    "    }\n" +
    "    try {\n" +
    "      doSomething()\n" +
    "    } catch (Exception e) {\n" +
    "      processException(e)\n" +
    "    } finally {\n" +
    "      processFinally()\n" +
    "    }\n" +
    "  }\n" +
    "    public static void test() \n" +
    "        throws Exception { \n" +
    "        foo.foo().bar(\"arg1\", \n" +
    "                      \"arg2\") \n" +
    "        new Object() {}" +
    "    } \n" +
    "    class TestInnerClass {}\n" +
    "    interface TestInnerInterface {}\n" +
    "}\n" +
    "\n" +
    "enum Breed {\n" +
    "    Dalmatian(), Labrador(), Dachshund()\n" +
    "}\n" +
    "\n" +
    "@Annotation1 @Annotation2 @Annotation3(param1=\"value1\", param2=\"value2\") @Annotation4 class Foo {\n" +
    "    @Annotation1 @Annotation3(param1=\"value1\", param2=\"value2\") public static void foo(){\n" +
    "    }\n" +
    "    @Annotation1 @Annotation3(param1=\"value1\", param2=\"value2\") public static int myFoo\n" +
    "    public void method(@Annotation1 @Annotation3(param1=\"value1\", param2=\"value2\") final int param){\n" +
    "        @Annotation1 @Annotation3(param1=\"value1\", param2=\"value2\") final int localVariable" +
    "    }\n" +
    "}";


  private static final String BLANK_LINE_SAMPLE =
    "/*\n" +
    " * This is a sample file.\n" +
    " */\n" +
    "package com.intellij.samples\n" +
    "\n" +
    "import com.intellij.idea.Main\n" +
    "\n" +
    "import javax.swing.*\n" +
    "import java.util.Vector\n" +
    "\n" +
    "public class Foo {\n" +
    "  private int field1\n" +
    "  private int field2\n" +
    "\n" +
    "  public void foo1() {\n" +
    "      new Runnable() {\n" +
    "          public void run() {\n" +
    "          }\n" +
    "      }\n" +
    "  }\n" +
    "\n" +
    "  public class InnerClass {\n" +
    "  }\n" +
    "}\n" +
    "class AnotherClass {\n" +
    "}\n" +
    "interface TestInterface {\n" +
    "    int MAX = 10\n" +
    "    int MIN = 1\n" +
    "    def method1()\n" +
    "    void method2()\n" +
    "}";

}
