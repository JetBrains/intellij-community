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

  Map<String, Object> DEFAULTS = Collections.unmodifiableMap(new HashMap<String, Object>() {{
    put(REMOVE_BRIDGE, "1");
    put(REMOVE_SYNTHETIC, "0");
    put(DECOMPILE_INNER, "1");
    put(DECOMPILE_CLASS_1_4, "1");
    put(DECOMPILE_ASSERTIONS, "1");
    put(HIDE_EMPTY_SUPER, "1");
    put(HIDE_DEFAULT_CONSTRUCTOR, "1");
    put(DECOMPILE_GENERIC_SIGNATURES, "0");
    put(NO_EXCEPTIONS_RETURN, "1");
    put(DECOMPILE_ENUM, "1");
    put(REMOVE_GET_CLASS_NEW, "1");
    put(LITERALS_AS_IS, "0");
    put(BOOLEAN_TRUE_ONE, "1");
    put(ASCII_STRING_CHARACTERS, "0");
    put(SYNTHETIC_NOT_SET, "1");
    put(UNDEFINED_PARAM_TYPE_OBJECT, "1");
    put(USE_DEBUG_VAR_NAMES, "1");
    put(REMOVE_EMPTY_RANGES, "1");
    put(FINALLY_DEINLINE, "1");
    put(IDEA_NOT_NULL_ANNOTATION, "1");
    put(LAMBDA_TO_ANONYMOUS_CLASS, "0");
    put(BYTECODE_SOURCE_MAPPING, "0");

    put(LOG_LEVEL, IFernflowerLogger.Severity.INFO.name());
    put(MAX_PROCESSING_METHOD, "0");
    put(RENAME_ENTITIES, "0");
    put(NEW_LINE_SEPARATOR, (InterpreterUtil.IS_WINDOWS ? "0" : "1"));
    put(INDENT_STRING, "   ");
    put(BANNER, "");
    put(UNIT_TEST_MODE, "0");
    put(DUMP_ORIGINAL_LINES, "0");
  }});
}
