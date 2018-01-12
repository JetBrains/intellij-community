/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.java.decompiler.main.extern;

import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public interface IFernflowerPreferences {
  String REMOVE_BRIDGE = "rbr";
  String REMOVE_SYNTHETIC = "rsy";
  String DECOMPILE_INNER = "din";
  String DECOMPILE_CLASS_1_4 = "dc4";
  String DECOMPILE_ASSERTIONS = "das";
  String HIDE_EMPTY_SUPER = "hes";
  String HIDE_DEFAULT_CONSTRUCTOR = "hdc";
  String DECOMPILE_GENERIC_SIGNATURES = "dgs";
  String NO_EXCEPTIONS_RETURN = "ner";
  String DECOMPILE_ENUM = "den";
  String REMOVE_GET_CLASS_NEW = "rgn";
  String LITERALS_AS_IS = "lit";
  String BOOLEAN_TRUE_ONE = "bto";
  String ASCII_STRING_CHARACTERS = "asc";
  String SYNTHETIC_NOT_SET = "nns";
  String UNDEFINED_PARAM_TYPE_OBJECT = "uto";
  String USE_DEBUG_VAR_NAMES = "udv";
  String USE_METHOD_PARAMETERS = "ump";
  String REMOVE_EMPTY_RANGES = "rer";
  String FINALLY_DEINLINE = "fdi";
  String IDEA_NOT_NULL_ANNOTATION = "inn";
  String LAMBDA_TO_ANONYMOUS_CLASS = "lac";
  String BYTECODE_SOURCE_MAPPING = "bsm";

  String LOG_LEVEL = "log";
  String MAX_PROCESSING_METHOD = "mpm";
  String RENAME_ENTITIES = "ren";
  String USER_RENAMER_CLASS = "urc";
  String NEW_LINE_SEPARATOR = "nls";
  String INDENT_STRING = "ind";
  String BANNER = "ban";

  String DUMP_ORIGINAL_LINES = "__dump_original_lines__";
  String UNIT_TEST_MODE = "__unit_test_mode__";

  String LINE_SEPARATOR_WIN = "\r\n";
  String LINE_SEPARATOR_UNX = "\n";

  Map<String, Object> DEFAULTS = getDefaults();

  static Map<String, Object> getDefaults() {
    Map<String, Object> defaults = new HashMap<>();

    defaults.put(REMOVE_BRIDGE, "1");
    defaults.put(REMOVE_SYNTHETIC, "0");
    defaults.put(DECOMPILE_INNER, "1");
    defaults.put(DECOMPILE_CLASS_1_4, "1");
    defaults.put(DECOMPILE_ASSERTIONS, "1");
    defaults.put(HIDE_EMPTY_SUPER, "1");
    defaults.put(HIDE_DEFAULT_CONSTRUCTOR, "1");
    defaults.put(DECOMPILE_GENERIC_SIGNATURES, "0");
    defaults.put(NO_EXCEPTIONS_RETURN, "1");
    defaults.put(DECOMPILE_ENUM, "1");
    defaults.put(REMOVE_GET_CLASS_NEW, "1");
    defaults.put(LITERALS_AS_IS, "0");
    defaults.put(BOOLEAN_TRUE_ONE, "1");
    defaults.put(ASCII_STRING_CHARACTERS, "0");
    defaults.put(SYNTHETIC_NOT_SET, "0");
    defaults.put(UNDEFINED_PARAM_TYPE_OBJECT, "1");
    defaults.put(USE_DEBUG_VAR_NAMES, "1");
    defaults.put(USE_METHOD_PARAMETERS, "1");
    defaults.put(REMOVE_EMPTY_RANGES, "1");
    defaults.put(FINALLY_DEINLINE, "1");
    defaults.put(IDEA_NOT_NULL_ANNOTATION, "1");
    defaults.put(LAMBDA_TO_ANONYMOUS_CLASS, "0");
    defaults.put(BYTECODE_SOURCE_MAPPING, "0");

    defaults.put(LOG_LEVEL, IFernflowerLogger.Severity.INFO.name());
    defaults.put(MAX_PROCESSING_METHOD, "0");
    defaults.put(RENAME_ENTITIES, "0");
    defaults.put(NEW_LINE_SEPARATOR, (InterpreterUtil.IS_WINDOWS ? "0" : "1"));
    defaults.put(INDENT_STRING, "   ");
    defaults.put(BANNER, "");
    defaults.put(UNIT_TEST_MODE, "0");
    defaults.put(DUMP_ORIGINAL_LINES, "0");

    return Collections.unmodifiableMap(defaults);
  }
}