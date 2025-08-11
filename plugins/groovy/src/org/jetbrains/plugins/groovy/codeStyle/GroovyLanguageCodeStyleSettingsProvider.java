// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeStyle;

import com.intellij.application.options.CodeStyleAbstractConfigurable;
import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.application.options.IndentOptionsEditor;
import com.intellij.application.options.SmartIndentOptionsEditor;
import com.intellij.application.options.codeStyle.properties.CodeStyleFieldAccessor;
import com.intellij.application.options.codeStyle.properties.CodeStylePropertyAccessor;
import com.intellij.application.options.codeStyle.properties.IntegerAccessor;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.codeStyle.*;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.fields.IntegerField;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyLanguage;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static com.intellij.openapi.util.io.StreamUtil.readText;
import static com.intellij.psi.codeStyle.CodeStyleSettingsCustomizableOptions.getInstance;
import static com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider.SettingsType.*;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author Rustam Vishnyakov
 */
public final class GroovyLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  @Override
  public @NotNull CodeStyleConfigurable createConfigurable(@NotNull CodeStyleSettings baseSettings,
                                                           @NotNull CodeStyleSettings modelSettings) {
    return new CodeStyleAbstractConfigurable(baseSettings, modelSettings, GroovyBundle.message("language.groovy")) {
      @Override
      protected @NotNull CodeStyleAbstractPanel createPanel(@NotNull CodeStyleSettings settings) {
        return new GroovyCodeStyleMainPanel(getCurrentSettings(), settings) {};
      }

      @Override
      public String getHelpTopic() {
        return "reference.settingsdialog.codestyle.groovy";
      }
    };
  }

  @Override
  public CustomCodeStyleSettings createCustomSettings(@NotNull CodeStyleSettings settings) {
    return new GroovyCodeStyleSettings(settings);
  }

  @Override
  public @NotNull Language getLanguage() {
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
        "KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE",

        "WRAP_LONG_LINES",

        "CLASS_BRACE_STYLE",
        "METHOD_BRACE_STYLE",
        "LAMBDA_BRACE_STYLE",
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
        "DOWHILE_BRACE_FORCE",
        "WHILE_ON_NEW_LINE",

        "INDENT_CASE_FROM_SWITCH",

        "RESOURCE_LIST_WRAP",
        "ALIGN_MULTILINE_RESOURCES",
        "RESOURCE_LIST_LPAREN_ON_NEXT_LINE",
        "RESOURCE_LIST_RPAREN_ON_NEXT_LINE",

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

        "ARRAY_INITIALIZER_WRAP",
        "ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION",
        "ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE",
        "ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE",

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
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "USE_FLYING_GEESE_BRACES",
                                GroovyBundle.message("code.style.option.use.flying.geese.braces"),
                                getInstance().WRAPPING_BRACES);

      consumer.showCustomOption(GroovyCodeStyleSettings.class,
                                "WRAP_CHAIN_CALLS_AFTER_DOT",
                                GroovyBundle.message("code.style.option.wrap.after.dot"),
                                getInstance().WRAPPING_CALL_CHAIN);

      consumer
        .showCustomOption(GroovyCodeStyleSettings.class, "ALIGN_MULTILINE_LIST_OR_MAP",
                          GroovyBundle.message("code.style.option.align.when.multiple"),
                          GroovyBundle.message("code.style.group.list.map.literals"));
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "ALIGN_NAMED_ARGS_IN_MAP",
                                GroovyBundle.message("code.style.option.align.multiline.named.arguments"),
                                GroovyBundle.message("code.style.group.list.map.literals"));
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "IMPORT_ANNOTATION_WRAP",
                                GroovyBundle.message("code.style.option.import.annotations"), null,
                                CodeStyleSettingsCustomizable.OptionAnchor.AFTER, "VARIABLE_ANNOTATION_WRAP",
                                getInstance().WRAP_OPTIONS, CodeStyleSettingsCustomizable.WRAP_VALUES);

      consumer.renameStandardOption("KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE",
                                    GroovyBundle.message("code.style.option.simple.lambdas.closures.in.one.line"));

      consumer.showCustomOption(GroovyCodeStyleSettings.class, "GINQ_GENERAL_CLAUSE_WRAP_POLICY", GroovyBundle.message("ginq.code.style.group.ginq.clauses"), null,
                                getInstance().WRAP_OPTIONS_FOR_SINGLETON, CodeStyleSettingsCustomizable.WRAP_VALUES_FOR_SINGLETON);

      consumer.showCustomOption(GroovyCodeStyleSettings.class, "GINQ_ON_WRAP_POLICY", GroovyBundle.message("ginq.code.style.option.wrap.on.clauses"), GroovyBundle.message("ginq.code.style.group.ginq.clauses"),
                                getInstance().WRAP_OPTIONS_FOR_SINGLETON, CodeStyleSettingsCustomizable.WRAP_VALUES_FOR_SINGLETON);

      consumer.showCustomOption(GroovyCodeStyleSettings.class, "GINQ_INDENT_ON_CLAUSE", GroovyBundle.message("ginq.code.style.option.indent.on.clauses"), GroovyBundle.message("ginq.code.style.group.ginq.clauses"));

      consumer.showCustomOption(GroovyCodeStyleSettings.class, "GINQ_HAVING_WRAP_POLICY", GroovyBundle.message("ginq.code.style.option.wrap.having.clauses"), GroovyBundle.message("ginq.code.style.group.ginq.clauses"),
                                getInstance().WRAP_OPTIONS_FOR_SINGLETON, CodeStyleSettingsCustomizable.WRAP_VALUES_FOR_SINGLETON);

      consumer.showCustomOption(GroovyCodeStyleSettings.class, "GINQ_INDENT_HAVING_CLAUSE", GroovyBundle.message("ginq.code.style.option.indent.having.clauses"), GroovyBundle.message("ginq.code.style.group.ginq.clauses"));

      consumer.showCustomOption(GroovyCodeStyleSettings.class, "GINQ_SPACE_AFTER_KEYWORD", GroovyBundle.message("ginq.code.style.option.space.after.keyword"), GroovyBundle.message("ginq.code.style.group.ginq.clauses"));

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
                                   "SPACE_AROUND_LAMBDA_ARROW",
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
                                   "SPACE_WITHIN_TRY_PARENTHESES",
                                   "SPACE_WITHIN_CATCH_PARENTHESES",
                                   "SPACE_WITHIN_SWITCH_PARENTHESES",
                                   "SPACE_WITHIN_SYNCHRONIZED_PARENTHESES",
                                   "SPACE_WITHIN_CAST_PARENTHESES",
                                   "SPACE_WITHIN_BRACKETS",
                                   "SPACE_WITHIN_BRACES",
                                   "SPACE_WITHIN_ARRAY_INITIALIZER_BRACES",
                                   "SPACE_WITHIN_EMPTY_ARRAY_INITIALIZER_BRACES",
                                   "SPACE_AFTER_TYPE_CAST",
                                   "SPACE_BEFORE_METHOD_CALL_PARENTHESES",
                                   "SPACE_BEFORE_METHOD_PARENTHESES",
                                   "SPACE_BEFORE_IF_PARENTHESES",
                                   "SPACE_BEFORE_WHILE_PARENTHESES",
                                   "SPACE_BEFORE_FOR_PARENTHESES",
                                   "SPACE_BEFORE_TRY_PARENTHESES",
                                   "SPACE_BEFORE_CATCH_PARENTHESES",
                                   "SPACE_BEFORE_SWITCH_PARENTHESES",
                                   "SPACE_BEFORE_SYNCHRONIZED_PARENTHESES",
                                   "SPACE_BEFORE_CLASS_LBRACE",
                                   "SPACE_BEFORE_METHOD_LBRACE",
                                   "SPACE_BEFORE_IF_LBRACE",
                                   "SPACE_BEFORE_ELSE_LBRACE",
                                   "SPACE_BEFORE_WHILE_LBRACE",
                                   "SPACE_BEFORE_FOR_LBRACE",
                                   "SPACE_BEFORE_DO_LBRACE",
                                   "SPACE_BEFORE_SWITCH_LBRACE",
                                   "SPACE_BEFORE_TRY_LBRACE",
                                   "SPACE_BEFORE_CATCH_LBRACE",
                                   "SPACE_BEFORE_FINALLY_LBRACE",
                                   "SPACE_BEFORE_SYNCHRONIZED_LBRACE",
                                   "SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE",
//                                   "SPACE_BEFORE_ANNOTATION_ARRAY_INITIALIZER_LBRACE",
                                   "SPACE_BEFORE_ELSE_KEYWORD",
                                   "SPACE_BEFORE_WHILE_KEYWORD",
                                   "SPACE_BEFORE_CATCH_KEYWORD",
                                   "SPACE_BEFORE_FINALLY_KEYWORD",
                                   "SPACE_BEFORE_QUEST",
                                   "SPACE_AFTER_QUEST",
                                   "SPACE_BEFORE_COLON",
                                   "SPACE_AFTER_COLON",
                                   "SPACE_BEFORE_ANOTATION_PARAMETER_LIST",
                                   "SPACE_WITHIN_ANNOTATION_PARENTHESES"
      );
      consumer.renameStandardOption("SPACE_AROUND_RELATIONAL_OPERATORS", GroovyBundle.message("code.style.option.relational.operators"));
      consumer.renameStandardOption("SPACE_AROUND_UNARY_OPERATOR", GroovyBundle.message("code.style.option.unary.operators"));
      consumer.renameStandardOption("SPACE_AROUND_LOGICAL_OPERATORS", GroovyBundle.message("code.style.option.logical.operators"));

      consumer.showCustomOption(GroovyCodeStyleSettings.class, "SPACE_IN_NAMED_ARGUMENT_BEFORE_COLON",
                                GroovyBundle.message("code.style.option.in.named.argument.before.colon"),
                                getInstance().SPACES_OTHER);
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "SPACE_IN_NAMED_ARGUMENT",
                                GroovyBundle.message("code.style.option.in.named.argument.after.colon"),
                                getInstance().SPACES_OTHER);
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "SPACE_WITHIN_LIST_OR_MAP",
                                GroovyBundle.message("code.style.option.list.maps.literals"),
                                getInstance().SPACES_WITHIN);
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "SPACE_BEFORE_CLOSURE_LBRACE",
                                GroovyBundle.message("code.style.option.closure.left.brace.in.method.calls"),
                                getInstance().SPACES_BEFORE_LEFT_BRACE);
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "SPACE_WITHIN_GSTRING_INJECTION_BRACES",
                                GroovyBundle.message("code.style.option.gstring.injection.braces"),
                                getInstance().SPACES_WITHIN);
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "SPACE_WITHIN_TUPLE_EXPRESSION",
                                GroovyBundle.message("code.style.option.tuple.assignment.expression"),
                                getInstance().SPACES_WITHIN);
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "SPACE_AROUND_REGEX_OPERATORS",
                                GroovyBundle.message("code.style.option.regexp.expression"),
                                getInstance().SPACES_AROUND_OPERATORS);
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "SPACE_BEFORE_ASSERT_SEPARATOR",
                                GroovyBundle.message("code.style.option.before.assert.separator"),
                                getInstance().SPACES_OTHER);
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "SPACE_AFTER_ASSERT_SEPARATOR",
                                GroovyBundle.message("code.style.option.after.assert.separator"),
                                getInstance().SPACES_OTHER);
      consumer.showCustomOption(GroovyCodeStyleSettings.class, "SPACE_BEFORE_RECORD_PARENTHESES",
                                GroovyBundle.message("code.style.option.before.record.parameter.list"), getInstance().SPACES_BEFORE_PARENTHESES);
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
    if (settingsType == COMMENTER_SETTINGS) {
      consumer.showStandardOptions(
        "LINE_COMMENT_ADD_SPACE",
        "LINE_COMMENT_ADD_SPACE_ON_REFORMAT",
        "LINE_COMMENT_AT_FIRST_COLUMN",
        "BLOCK_COMMENT_AT_FIRST_COLUMN",
        "BLOCK_COMMENT_ADD_SPACE"
      );
      return;
    }
    consumer.showAllStandardOptions();
  }

  @Override
  protected void customizeDefaults(@NotNull CommonCodeStyleSettings commonSettings,
                                   @NotNull CommonCodeStyleSettings.IndentOptions indentOptions) {
    commonSettings.SPACE_WITHIN_BRACES = true;
    commonSettings.KEEP_SIMPLE_CLASSES_IN_ONE_LINE = true;
    commonSettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    commonSettings.KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = true;
  }

  @Override
  public String getCodeSample(@NotNull SettingsType settingsType) {
    if (settingsType == BLANK_LINES_SETTINGS ||
        settingsType == SPACING_SETTINGS ||
        settingsType == WRAPPING_AND_BRACES_SETTINGS ||
        settingsType == INDENT_SETTINGS) {
        return loadSample(settingsType);
    }
    return null;
  }

  private static String loadSample(@NotNull SettingsType settingsType) {
    String name = "/samples/" + settingsType.name() + ".txt";
    try {
      try (Reader reader = new InputStreamReader(GroovyLanguageCodeStyleSettingsProvider.class.getResourceAsStream(name), UTF_8)) {
        return readText(reader);
      }
    }
    catch (IOException ignored) { }
    return "";
  }

  @Override
  public IndentOptionsEditor getIndentOptionsEditor() {
    return new SmartIndentOptionsEditor() {
      private JLabel myLabelIndentLabel;
      private IntegerField myLabelIndent;

      private JBLabel myStyleLabel;
      private JComboBox<LabelIndentStyle> myLabelIndentStyle;

      @Override
      protected void addComponents() {
        super.addComponents();

        myLabelIndentLabel = new JLabel(ApplicationBundle.message("editbox.indent.label.indent"));
        myLabelIndent = new IntegerField(GroovyBundle.message("settings.code.style.label.indent.size"), 0, Integer.MAX_VALUE);
        add(myLabelIndentLabel, myLabelIndent);

        myStyleLabel = new JBLabel(GroovyBundle.message("settings.code.style.label.indent.style"));
        myLabelIndentStyle = new ComboBox<>(new EnumComboBoxModel<>(LabelIndentStyle.class));
        add(myStyleLabel, myLabelIndentStyle);
      }

      @Override
      public boolean isModified(final CodeStyleSettings settings, final CommonCodeStyleSettings.IndentOptions options) {
        return super.isModified(settings, options) ||
               myLabelIndent.getValue() != options.LABEL_INDENT_SIZE ||
               isLabelStyleModified(
                 options.LABEL_INDENT_ABSOLUTE,
                 settings.getCustomSettings(GroovyCodeStyleSettings.class).INDENT_LABEL_BLOCKS
               );
      }

      private boolean isLabelStyleModified(boolean absolute, boolean relative) {
        Object selectedStyle = myLabelIndentStyle.getSelectedItem();
        if (absolute) {
          return selectedStyle != LabelIndentStyle.ABSOLUTE;
        }
        else if (relative) {
          return selectedStyle != LabelIndentStyle.RELATIVE;
        }
        else {
          return selectedStyle != LabelIndentStyle.RELATIVE_REVERSED;
        }
      }

      @Override
      public void apply(final CodeStyleSettings settings, final CommonCodeStyleSettings.IndentOptions options) {
        super.apply(settings, options);
        options.LABEL_INDENT_SIZE = myLabelIndent.getValue();
        LabelIndentStyle labelIndentStyle = (LabelIndentStyle)myLabelIndentStyle.getSelectedItem();
        options.LABEL_INDENT_ABSOLUTE = labelIndentStyle == LabelIndentStyle.ABSOLUTE;
        settings.getCustomSettings(GroovyCodeStyleSettings.class).INDENT_LABEL_BLOCKS = labelIndentStyle == LabelIndentStyle.RELATIVE;
      }

      @Override
      public void reset(final @NotNull CodeStyleSettings settings, final @NotNull CommonCodeStyleSettings.IndentOptions options) {
        super.reset(settings, options);
        myLabelIndent.setValue(options.LABEL_INDENT_SIZE);
        if (options.LABEL_INDENT_ABSOLUTE) {
          myLabelIndentStyle.setSelectedItem(LabelIndentStyle.ABSOLUTE);
        }
        else if (settings.getCustomSettings(GroovyCodeStyleSettings.class).INDENT_LABEL_BLOCKS) {
          myLabelIndentStyle.setSelectedItem(LabelIndentStyle.RELATIVE);
        }
        else {
          myLabelIndentStyle.setSelectedItem(LabelIndentStyle.RELATIVE_REVERSED);
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

  private enum LabelIndentStyle {
    ABSOLUTE(GroovyBundle.messagePointer("settings.code.style.absolute")),
    RELATIVE(GroovyBundle.messagePointer("settings.code.style.indent.statements.after.label")),
    RELATIVE_REVERSED(GroovyBundle.messagePointer("settings.code.style.indent.labels"));

    private final Supplier<String> description;

    LabelIndentStyle(Supplier<@NotNull @Nls String> description) {
      this.description = description;
    }

    @Override
    public String toString() {
      return description.get();
    }
  }

  @Override
  public @Nullable CodeStyleFieldAccessor getAccessor(@NotNull Object codeStyleObject,
                                                      @NotNull Field field) {
    if (PackageEntryTable.class.isAssignableFrom(field.getType())) {
      return new JavaPackageEntryTableAccessor(codeStyleObject, field);
    }
    if (codeStyleObject instanceof GroovyCodeStyleSettings) {
      if (field.getName().endsWith("_ORDER_WEIGHT")) {
        // Ignore all ORDER_WEIGHT_FIELDS for now
        // TODO: Needs a way to translate several fields to a set of values (single property)
        return new IntegerAccessor(codeStyleObject, field) {
          @Override
          public boolean isIgnorable() {
            return true;
          }
        };
      }
    }
    return super.getAccessor(codeStyleObject, field);
  }

  @Override
  public List<CodeStylePropertyAccessor> getAdditionalAccessors(@NotNull Object codeStyleObject) {
    if (codeStyleObject instanceof GroovyCodeStyleSettings) {
      try {
        Field onDemandPackagesField = codeStyleObject.getClass().getField("PACKAGES_TO_USE_IMPORT_ON_DEMAND");
        return Collections.singletonList(new JavaPackageEntryTableAccessor(codeStyleObject, onDemandPackagesField) {
          @Override
          public boolean set(@NotNull List<String> extVal) {
            PackageEntryTable entryTable = fromExternal(extVal);
            if (entryTable != null) {
              ((GroovyCodeStyleSettings)codeStyleObject).getPackagesToUseImportOnDemand().copyFrom(entryTable);
              return true;
            }
            return false;
          }
        });
      }
      catch (NoSuchFieldException e) {
        // Ignore
      }
    }
    return super.getAdditionalAccessors(codeStyleObject);
  }

  @Override
  public boolean usesCommonKeepLineBreaks() {
    return true;
  }
}
