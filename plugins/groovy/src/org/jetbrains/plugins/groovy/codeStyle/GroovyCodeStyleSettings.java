// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeStyle;

import com.intellij.application.options.CodeStyle;
import com.intellij.configurationStore.Property;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author Max Medvedev
 */
public class GroovyCodeStyleSettings extends CustomCodeStyleSettings implements ImportsLayoutSettings {
  public int STATIC_FIELDS_ORDER_WEIGHT = 1;
  public int FIELDS_ORDER_WEIGHT = 2;
  public int CONSTRUCTORS_ORDER_WEIGHT = 3;
  public int STATIC_METHODS_ORDER_WEIGHT = 4;
  public int METHODS_ORDER_WEIGHT = 5;
  public int STATIC_INNER_CLASSES_ORDER_WEIGHT = 6;
  public int INNER_CLASSES_ORDER_WEIGHT = 7;

  /**
   * Defines if 'flying geese' style should be used for curly braces formatting, e.g. if we want to format code like
   * <p/>
   * <pre>
   *     class Test {
   *         {
   *             System.out.println();
   *         }
   *     }
   * </pre>
   * to
   * <pre>
   *     class Test { {
   *         System.out.println();
   *     } }
   * </pre>
   */
  public boolean USE_FLYING_GEESE_BRACES = false;

  public boolean SPACE_IN_NAMED_ARGUMENT_BEFORE_COLON = false;
  public boolean SPACE_IN_NAMED_ARGUMENT = true;
  public boolean ALIGN_MULTILINE_LIST_OR_MAP = true;
  public boolean WRAP_CHAIN_CALLS_AFTER_DOT = false;
  public boolean SPACE_WITHIN_LIST_OR_MAP = false;
  public boolean ALIGN_NAMED_ARGS_IN_MAP = true;
  @Property(externalName = "space_before_closure_left_brace")
  public boolean SPACE_BEFORE_CLOSURE_LBRACE = true;
  public boolean SPACE_WITHIN_GSTRING_INJECTION_BRACES = false;
  public boolean SPACE_WITHIN_TUPLE_EXPRESSION = false;
  public boolean INDENT_LABEL_BLOCKS = true;
  public boolean SPACE_AROUND_REGEX_OPERATORS = true;
  public boolean SPACE_BEFORE_ASSERT_SEPARATOR = false;
  public boolean SPACE_AFTER_ASSERT_SEPARATOR = true;

  /**
   * "record R (int param) {}"
   * or
   * "record R(int param) {}"
   */
  public boolean SPACE_BEFORE_RECORD_PARENTHESES = false;

  public boolean ENABLE_GROOVYDOC_FORMATTING = true;

  // GINQ
  public int GINQ_GENERAL_CLAUSE_WRAP_POLICY = CommonCodeStyleSettings.WRAP_ALWAYS;
  public int GINQ_ON_WRAP_POLICY = CommonCodeStyleSettings.WRAP_AS_NEEDED;
  public boolean GINQ_INDENT_ON_CLAUSE = true;
  public int GINQ_HAVING_WRAP_POLICY = CommonCodeStyleSettings.WRAP_AS_NEEDED;
  public boolean GINQ_INDENT_HAVING_CLAUSE = true;
  public boolean GINQ_SPACE_AFTER_KEYWORD = true;

  //imports
  public boolean USE_FQ_CLASS_NAMES = false;
  public boolean USE_FQ_CLASS_NAMES_IN_JAVADOC = true;
  public boolean USE_SINGLE_CLASS_IMPORTS = true;
  public boolean INSERT_INNER_CLASS_IMPORTS = false;
  public int CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 5;
  public int NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = 3;
  public final PackageEntryTable PACKAGES_TO_USE_IMPORT_ON_DEMAND = new PackageEntryTable();
  @Property(externalName = "imports_layout")
  public PackageEntryTable IMPORT_LAYOUT_TABLE = new PackageEntryTable();
  public boolean LAYOUT_STATIC_IMPORTS_SEPARATELY = true;

  public int IMPORT_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;

  private void initImportsByDefault() {
    PACKAGES_TO_USE_IMPORT_ON_DEMAND.addEntry(new PackageEntry(false, "java.awt", false));
    PACKAGES_TO_USE_IMPORT_ON_DEMAND.addEntry(new PackageEntry(false, "javax.swing", false));
    IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY);
    IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.BLANK_LINE_ENTRY);
    IMPORT_LAYOUT_TABLE.addEntry(new PackageEntry(false, "javax", true));
    IMPORT_LAYOUT_TABLE.addEntry(new PackageEntry(false, "java", true));
    IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.BLANK_LINE_ENTRY);
    IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);
  }

  public GroovyCodeStyleSettings(CodeStyleSettings container) {
    super("GroovyCodeStyleSettings", container);

    initImportsByDefault();
  }

  @Override
  public int getNamesCountToUseImportOnDemand() {
    return NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND;
  }

  @Override
  public void setNamesCountToUseImportOnDemand(int value) {
    NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = value;
  }

  @Override
  public int getClassCountToUseImportOnDemand() {
    return CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND;
  }

  @Override
  public void setClassCountToUseImportOnDemand(int value) {
    CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = value;
  }

  @Override
  public boolean isInsertInnerClassImports() {
    return INSERT_INNER_CLASS_IMPORTS;
  }

  @Override
  public void setInsertInnerClassImports(boolean value) {
    INSERT_INNER_CLASS_IMPORTS = value;
  }

  @Override
  public boolean isUseSingleClassImports() {
    return USE_SINGLE_CLASS_IMPORTS;
  }

  @Override
  public void setUseSingleClassImports(boolean value) {
    USE_SINGLE_CLASS_IMPORTS = value;
  }

  @Override
  public boolean isUseFqClassNames() {
    return USE_FQ_CLASS_NAMES;
  }

  @Override
  public void setUseFqClassNames(boolean value) {
    USE_FQ_CLASS_NAMES = value;
  }

  @Override
  public PackageEntryTable getImportLayoutTable() {
    return IMPORT_LAYOUT_TABLE;
  }

  @Override
  public PackageEntryTable getPackagesToUseImportOnDemand() {
    return PACKAGES_TO_USE_IMPORT_ON_DEMAND;
  }

  @Override
  public boolean isLayoutStaticImportsSeparately() {
    return LAYOUT_STATIC_IMPORTS_SEPARATELY;
  }

  @Override
  public void setLayoutStaticImportsSeparately(boolean value) {
    LAYOUT_STATIC_IMPORTS_SEPARATELY = value;
  }

  public boolean isGroovyDocFormattingAllowed() {
    return ENABLE_GROOVYDOC_FORMATTING;
  }

  @SuppressWarnings("deprecation")
  @Override
  protected void importLegacySettings(@NotNull CodeStyleSettings rootSettings) {
    STATIC_FIELDS_ORDER_WEIGHT = rootSettings.STATIC_FIELDS_ORDER_WEIGHT;
    FIELDS_ORDER_WEIGHT = rootSettings.FIELDS_ORDER_WEIGHT;
    CONSTRUCTORS_ORDER_WEIGHT = rootSettings.CONSTRUCTORS_ORDER_WEIGHT;
    STATIC_METHODS_ORDER_WEIGHT = rootSettings.STATIC_METHODS_ORDER_WEIGHT;
    METHODS_ORDER_WEIGHT = rootSettings.METHODS_ORDER_WEIGHT;
    STATIC_INNER_CLASSES_ORDER_WEIGHT = rootSettings.STATIC_INNER_CLASSES_ORDER_WEIGHT;
    INNER_CLASSES_ORDER_WEIGHT = rootSettings.INNER_CLASSES_ORDER_WEIGHT;
  }

  @NotNull
  public static GroovyCodeStyleSettings getInstance(@NotNull PsiFile file) {
    return CodeStyle.getCustomSettings(file, GroovyCodeStyleSettings.class);
  }

  @NotNull
  public static GroovyCodeStyleSettings getInstance(@NotNull Editor editor) {
    return CodeStyle.getSettings(editor).getCustomSettings(GroovyCodeStyleSettings.class);
  }
}
