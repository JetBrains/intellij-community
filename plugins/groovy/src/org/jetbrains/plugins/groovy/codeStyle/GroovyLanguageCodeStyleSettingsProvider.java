// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeStyle;

import com.intellij.application.options.IndentOptionsEditor;
import com.intellij.application.options.SmartIndentOptionsEditor;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;

import javax.swing.*;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

import static com.intellij.openapi.util.io.StreamUtil.readText;
import static com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider.SettingsType.*;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author Rustam Vishnyakov
 */
public class GroovyLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {

  public static final String ABSOLUTE = "Absolute";
  public static final String RELATIVE = "Indent statements after label";
  public static final String RELATIVE_REVERSED = "Indent labels";

  @NotNull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }

  @Override
  public void customizeSettings(@NotNull CodeStyleSettingsCustomizable consumer,
                                @NotNull SettingsType settingsType) {
    if (settingsType == WRAPPING_AND_BRACES_SETTINGS) {
      consumer.showStandardOptions(
        "RIGHT_MARGIN",
        "WRAP_ON_TYPING",
        "KEEP_LINE_BREAKS",
        "KEEP_FIRST_COLUMN_COMMENT",
        "KEEP_CONTROL_STATEMENT_IN_ONE_LINE",
        "KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE",
        "KEEP_SIMPLE_BLOCKS_IN_ONE_LINE",
        "KEEP_SIMPLE_METHODS_IN_ONE_LINE",
        "KEEP_SIMPLE_CLASSES_IN_ONE_LINE",

        "WRAP_LONG_LINES",

        "CLASS_BRACE_STYLE",
        "METHOD_BRACE_STYLE",
        "BRACE_STYLE",

        "EXTENDS_LIST_WRAP",
        "ALIGN_MULTILINE_EXTENDS_LIST",

        "EXTENDS_KEYWORD_WRAP",

        "THROWS_LIST_WRAP",
        "ALIGN_MULTILINE_THROWS_LIST",
        "ALIGN_THROWS_KEYWORD",
        "THROWS_KEYWORD_WRAP",

        "METHOD_PARAMETERS_WRAP",
        "ALIGN_MULTILINE_PARAMETERS",
        "METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE",
        "METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE",

        "CALL_PARAMETERS_WRAP",
        "ALIGN_MULTILINE_PARAMETERS_IN_CALLS",
        "PREFER_PARAMETERS_WRAP",
        "CALL_PARAMETERS_LPAREN_ON_NEXT_LINE",
        "CALL_PARAMETERS_RPAREN_ON_NEXT_LINE",

        "ALIGN_MULTILINE_METHOD_BRACKETS",

        "METHOD_CALL_CHAIN_WRAP",
        "ALIGN_MULTILINE_CHAINED_METHODS",

        "ALIGN_GROUP_FIELD_DECLARATIONS",

        "IF_BRACE_FORCE",
        "ELSE_ON_NEW_LINE",
        "SPECIAL_ELSE_IF_TREATMENT",

        "FOR_STATEMENT_WRAP",
        "ALIGN_MULTILINE_FOR",
        "FOR_STATEMENT_LPAREN_ON_NEXT_LINE",
        "FOR_STATEMENT_RPAREN_ON_NEXT_LINE",
        "FOR_BRACE_FORCE",

        "WHILE_BRACE_FORCE",
        //"DOWHILE_BRACE_FORCE",
        //"WHILE_ON_NEW_LINE",

        "INDENT_CASE_FROM_SWITCH",

        //"RESOURCE_LIST_WRAP",
        //"ALIGN_MULTILINE_RESOURCES",
        //"RESOURCE_LIST_LPAREN_ON_NEXT_LINE",
        //"RESOURCE_LIST_RPAREN_ON_NEXT_LINE",

        "CATCH_ON_NEW_LINE",
        "FINALLY_ON_NEW_LINE",

        "BINARY_OPERATION_WRAP",
        "ALIGN_MULTILINE_BINARY_OPERATION",
        //"BINARY_OPERATION_SIGN_ON_NEXT_LINE",
        //"ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION",
        "PARENTHESES_EXPRESSION_LPAREN_WRAP",
        "PARENTHESES_EXPRESSION_RPAREN_WRAP",

        "ASSIGNMENT_WRAP",
        "ALIGN_MULTILINE_ASSIGNMENT",
        //"PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE",

        "TERNARY_OPERATION_WRAP",
        "ALIGN_MULTILINE_TERNARY_OPERATION",
        //"TERNARY_OPERATION_SIGNS_ON_NEXT_LINE",

        //"ARRAY_INITIALIZER_WRAP",
        //"ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION",
        //"ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE",
        //"ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE",

        "MODIFIER_LIST_WRAP",

        "ASSERT_STATEMENT_WRAP",
        //"ASSERT_STATEMENT_COLON_ON_NEXT_LINE",

        "CLASS_ANNOTATION_WRAP",
        "METHOD_ANNOTATION_WRAP",
        "FIELD_ANNOTATION_WRAP",
        "PARAMETER_ANNOTATION_WRAP",
        "VARIABLE_ANNOTATION_WRAP",
        "ENUM_CONSTANTS_WRAP"
      );
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "USE_FLYING_GEESE_BRACES", "Use flying geese braces",
                                CodeStyleSettingsCustomizable.WRAPPING_BRACES);
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "ALIGN_MULTILINE_LIST_OR_MAP", "Align when multiple",     "List and map literals");
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "ALIGN_NAMED_ARGS_IN_MAP",     "Align multiline named arguments",   "List and map literals");
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "IMPORT_ANNOTATION_WRAP", "Import annotations", null,
                                CodeStyleSettingsCustomizable.OptionAnchor.AFTER, "VARIABLE_ANNOTATION_WRAP",
                                CodeStyleSettingsCustomizable.WRAP_OPTIONS, CodeStyleSettingsCustomizable.WRAP_VALUES);
      return;
    }
    if (settingsType == SPACING_SETTINGS) {
      consumer.showStandardOptions("INSERT_FIRST_SPACE_IN_LINE",
                                   "SPACE_AROUND_ASSIGNMENT_OPERATORS",
                                   "SPACE_AROUND_LOGICAL_OPERATORS",
                                   "SPACE_AROUND_EQUALITY_OPERATORS",
                                   "SPACE_AROUND_RELATIONAL_OPERATORS",
                                   "SPACE_AROUND_BITWISE_OPERATORS",
                                   "SPACE_AROUND_ADDITIVE_OPERATORS",
                                   "SPACE_AROUND_MULTIPLICATIVE_OPERATORS",
                                   "SPACE_AROUND_SHIFT_OPERATORS",
                                   //"SPACE_AROUND_UNARY_OPERATOR",
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
                                   "SPACE_BEFORE_ANOTATION_PARAMETER_LIST",
                                   "SPACE_WITHIN_ANNOTATION_PARENTHESES"
      );
      consumer.renameStandardOption("SPACE_AROUND_RELATIONAL_OPERATORS", "Relational operators (<, >, <=, >=, <=>)");
      consumer.renameStandardOption("SPACE_AROUND_UNARY_OPERATOR", "Unary operators (!, -, +, ++, --, *)");

      consumer.showCustomOption(GroovyCodeStyleSettings.class, "SPACE_IN_NAMED_ARGUMENT_BEFORE_COLON" , "In named argument before ':'", CodeStyleSettingsCustomizable.SPACES_OTHER);
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "SPACE_IN_NAMED_ARGUMENT", "In named argument after ':'", CodeStyleSettingsCustomizable.SPACES_OTHER);
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "SPACE_WITHIN_LIST_OR_MAP", "List and maps literals", CodeStyleSettingsCustomizable.SPACES_WITHIN);
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "SPACE_BEFORE_CLOSURE_LBRACE", "Closure left brace in method calls", CodeStyleSettingsCustomizable.SPACES_BEFORE_LEFT_BRACE);
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "SPACE_WITHIN_GSTRING_INJECTION_BRACES", "GString injection braces", CodeStyleSettingsCustomizable.SPACES_WITHIN);
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "SPACE_WITHIN_TUPLE_EXPRESSION", "Tuple assignment expression", CodeStyleSettingsCustomizable.SPACES_WITHIN);
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "SPACE_AROUND_REGEX_OPERATORS", "Regexp expression (==~, =~)", CodeStyleSettingsCustomizable.SPACES_AROUND_OPERATORS);
      return;
    }
    if (settingsType == BLANK_LINES_SETTINGS) {

      consumer.showStandardOptions(
        "KEEP_BLANK_LINES_IN_DECLARATIONS",
        "KEEP_BLANK_LINES_IN_CODE",
        "KEEP_BLANK_LINES_BEFORE_RBRACE",

        "BLANK_LINES_BEFORE_PACKAGE",
        "BLANK_LINES_AFTER_PACKAGE",
        "BLANK_LINES_BEFORE_IMPORTS",
        "BLANK_LINES_AFTER_IMPORTS",
        "BLANK_LINES_AROUND_CLASS",
        "BLANK_LINES_AFTER_CLASS_HEADER",
        //"BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER",
        "BLANK_LINES_AROUND_FIELD_IN_INTERFACE",
        "BLANK_LINES_AROUND_FIELD",
        "BLANK_LINES_AROUND_METHOD_IN_INTERFACE",
        "BLANK_LINES_AROUND_METHOD",
        "BLANK_LINES_BEFORE_METHOD_BODY"
      );
      return;
    }
    consumer.showAllStandardOptions();
  }

  @Override
  public CommonCodeStyleSettings getDefaultCommonSettings() {
    CommonCodeStyleSettings defaultSettings = new CommonCodeStyleSettings(GroovyLanguage.INSTANCE);
    defaultSettings.initIndentOptions();
    defaultSettings.SPACE_WITHIN_BRACES = true;
    defaultSettings.KEEP_SIMPLE_CLASSES_IN_ONE_LINE = true;
    defaultSettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    return defaultSettings;
  }

  @Override
  public String getCodeSample(@NotNull SettingsType settingsType) {
    return SAMPLES.get(settingsType);
  }

  private static final Map<SettingsType, String> SAMPLES;

  static {
    Map<SettingsType, String> samples = new EnumMap<>(SettingsType.class);
    for (SettingsType type: new SettingsType[]{BLANK_LINES_SETTINGS, SPACING_SETTINGS, WRAPPING_AND_BRACES_SETTINGS, INDENT_SETTINGS}) {
      samples.put(type, loadSample(type));
    }
    SAMPLES = samples;
  }

  private static String loadSample(@NotNull SettingsType settingsType) {
    String name = "/samples/" + settingsType.name() + ".txt";
    try {
      return readText(
        GroovyLanguageCodeStyleSettingsProvider.class.getResourceAsStream(name), UTF_8
      );
    }
    catch (IOException ignored) {
    }
    return "";
  }

  @Override
  public IndentOptionsEditor getIndentOptionsEditor() {
    return new SmartIndentOptionsEditor() {
      private JTextField myLabelIndent;
      private JLabel myLabelIndentLabel;

      private JComboBox myLabelIndentStyle;
      private JBLabel myStyleLabel;

      @Override
      protected void addComponents() {
        super.addComponents();

        myLabelIndent = new JTextField(4);
        add(myLabelIndentLabel = new JLabel(ApplicationBundle.message("editbox.indent.label.indent")), myLabelIndent);

        myStyleLabel = new JBLabel("Label indent style:");

        myLabelIndentStyle = new JComboBox(new Object[] {ABSOLUTE, RELATIVE, RELATIVE_REVERSED});
        add(myStyleLabel, myLabelIndentStyle);
      }

      @Override
      public boolean isModified(final CodeStyleSettings settings, final CommonCodeStyleSettings.IndentOptions options) {
        boolean isModified = super.isModified(settings, options);

        isModified |= isFieldModified(myLabelIndent, options.LABEL_INDENT_SIZE);
        isModified |= isLabelStyleModified(options.LABEL_INDENT_ABSOLUTE, settings.getCustomSettings(GroovyCodeStyleSettings.class).INDENT_LABEL_BLOCKS);
        return isModified;
      }

      private boolean isLabelStyleModified(boolean absolute, boolean relative) {
        if (absolute) {
          return !ABSOLUTE.equals(myLabelIndentStyle.getSelectedItem());
        }
        else if (relative) {
          return !RELATIVE.equals(myLabelIndentStyle.getSelectedItem());
        }
        else {
          return !RELATIVE_REVERSED.equals(myLabelIndentStyle.getSelectedItem());
        }
      }

      @Override
      public void apply(final CodeStyleSettings settings, final CommonCodeStyleSettings.IndentOptions options) {
        super.apply(settings, options);
        options.LABEL_INDENT_SIZE = getFieldValue(myLabelIndent, Integer.MIN_VALUE, options.LABEL_INDENT_SIZE);
        options.LABEL_INDENT_ABSOLUTE = ABSOLUTE.equals(myLabelIndentStyle.getSelectedItem());
        settings.getCustomSettings(GroovyCodeStyleSettings.class).INDENT_LABEL_BLOCKS = RELATIVE.equals(myLabelIndentStyle.getSelectedItem());
      }

      @Override
      public void reset(@NotNull final CodeStyleSettings settings, @NotNull final CommonCodeStyleSettings.IndentOptions options) {
        super.reset(settings, options);
        myLabelIndent.setText(Integer.toString(options.LABEL_INDENT_SIZE));
        if (options.LABEL_INDENT_ABSOLUTE) {
          myLabelIndentStyle.setSelectedItem(ABSOLUTE);
        }
        else if(settings.getCustomSettings(GroovyCodeStyleSettings.class).INDENT_LABEL_BLOCKS) {
          myLabelIndentStyle.setSelectedItem(RELATIVE);
        }
        else {
          myLabelIndentStyle.setSelectedItem(RELATIVE_REVERSED);
        }
      }

      @Override
      public void setEnabled(final boolean enabled) {
        super.setEnabled(enabled);
        myLabelIndent.setEnabled(enabled);
        myLabelIndentLabel.setEnabled(enabled);
        myStyleLabel.setEnabled(enabled);
        myLabelIndentStyle.setEnabled(enabled);
      }
    };
  }
}
