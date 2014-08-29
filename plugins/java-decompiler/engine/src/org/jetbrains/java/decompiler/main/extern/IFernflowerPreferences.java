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
package org.jetbrains.java.decompiler.main.extern;

public interface IFernflowerPreferences {

  String REMOVE_BRIDGE = "rbr";
  String REMOVE_SYNTHETIC = "rsy";
  String DECOMPILE_INNER = "din";
  String DECOMPILE_CLASS_1_4 = "dc4";
  String DECOMPILE_ASSERTIONS = "das";
  String HIDE_EMPTY_SUPER = "hes";
  String HIDE_DEFAULT_CONSTRUCTOR = "hdc";
  String DECOMPILE_GENERIC_SIGNATURES = "dgs";
  String OUTPUT_COPYRIGHT_COMMENT = "occ";
  String NO_EXCEPTIONS_RETURN = "ner";
  String DECOMPILE_ENUM = "den";
  String REMOVE_GETCLASS_NEW = "rgn";
  String LITERALS_AS_IS = "lit";
  String BOOLEAN_TRUE_ONE = "bto";
  String SYNTHETIC_NOT_SET = "nns";
  String UNDEFINED_PARAM_TYPE_OBJECT = "uto";
  String USE_DEBUG_VARNAMES = "udv";
  String MAX_PROCESSING_METHOD = "mpm";
  String REMOVE_EMPTY_RANGES = "rer";
  String ASCII_STRING_CHARACTERS = "asc";

  String FINALLY_DEINLINE = "fdi";

  String FINALLY_CATCHALL = "FINALLY_CATCHALL";
  String FINALLY_SEMAPHOR = "FINALLY_SEMAPHOR";

  String RENAME_ENTITIES = "ren";
  String USER_RENAMER_CLASS = "urc";

  String LOG_LEVEL = "log";

  String NEW_LINE_SEPARATOR = "nls";
  String IDEA_NOT_NULL_ANNOTATION = "inn";
  String LAMBDA_TO_ANONYMOUS_CLASS = "lac";
  String INDENT_STRING = "ind";

  String LINE_SEPARATOR_WIN = "\r\n";
  String LINE_SEPARATOR_LIN = "\n";
}
