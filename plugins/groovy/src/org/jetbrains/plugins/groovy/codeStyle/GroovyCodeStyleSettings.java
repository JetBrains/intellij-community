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
package org.jetbrains.plugins.groovy.codeStyle;

import com.intellij.psi.codeStyle.*;

/**
 * @author Max Medvedev
 */
public class GroovyCodeStyleSettings extends CustomCodeStyleSettings {
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

  public boolean SPACE_IN_NAMED_ARGUMENT = true;
  public boolean ALIGN_MULTILINE_LIST_OR_MAP = true;
  public boolean SPACE_WITHIN_LIST_OR_MAP = false;
  public boolean ALIGN_NAMED_ARGS_IN_MAP = true;
  public boolean SPACE_BEFORE_CLOSURE_LBRACE = true;
  public boolean SPACE_WITHIN_GSTRING_INJECTION_BRACES = false;
  public boolean SPACE_WITHIN_TUPLE_EXPRESSION = false;
  public boolean INDENT_LABEL_BLOCKS = true;
  public boolean SPACE_AROUND_REGEX_OPERATORS = true;

  //imports
  public boolean USE_FQ_CLASS_NAMES = false;
  public boolean USE_FQ_CLASS_NAMES_IN_JAVADOC = true;
  public boolean USE_SINGLE_CLASS_IMPORTS = true;
  public boolean INSERT_INNER_CLASS_IMPORTS = false;
  public int CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 5;
  public int NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = 3;
  public final PackageEntryTable PACKAGES_TO_USE_IMPORT_ON_DEMAND = new PackageEntryTable();
  public final PackageEntryTable IMPORT_LAYOUT_TABLE = new PackageEntryTable();
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
}
