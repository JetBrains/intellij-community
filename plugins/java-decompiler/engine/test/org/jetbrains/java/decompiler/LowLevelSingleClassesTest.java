// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.Map;

public class LowLevelSingleClassesTest extends SingleClassesTestBase {

  @Rule
  public Timeout globalTimeout = Timeout.seconds(60);

  @Override
  protected Map<String, String> getDecompilerOptions() {
    return Map.ofEntries(Map.entry(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1"),
                         Map.entry(IFernflowerPreferences.DUMP_ORIGINAL_LINES, "1"),
                         Map.entry(IFernflowerPreferences.IGNORE_INVALID_BYTECODE, "1"),
                         Map.entry(IFernflowerPreferences.VERIFY_ANONYMOUS_CLASSES, "1"),
                         Map.entry(IFernflowerPreferences.INLINE_SIMPLE_LAMBDAS, "1"),
                         Map.entry(IFernflowerPreferences.CHECK_CLOSABLE_INTERFACE, "0"),
                         Map.entry(IFernflowerPreferences.HIDE_RECORD_CONSTRUCTOR_AND_GETTERS, "0"),
                         Map.entry(IFernflowerPreferences.REMOVE_BRIDGE, "0"),
                         Map.entry(IFernflowerPreferences.REMOVE_SYNTHETIC, "0"),
                         Map.entry(IFernflowerPreferences.DECOMPILE_INNER, "1"),
                         Map.entry(IFernflowerPreferences.DECOMPILE_ASSERTIONS, "0"),
                         Map.entry(IFernflowerPreferences.HIDE_EMPTY_SUPER, "0"),
                         Map.entry(IFernflowerPreferences.HIDE_DEFAULT_CONSTRUCTOR, "0"),
                         Map.entry(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1"),
                         Map.entry(IFernflowerPreferences.NO_EXCEPTIONS_RETURN, "1"),
                         Map.entry(IFernflowerPreferences.DECOMPILE_ENUM, "0"),
                         Map.entry(IFernflowerPreferences.LITERALS_AS_IS, "1"),
                         Map.entry(IFernflowerPreferences.REMOVE_GET_CLASS_NEW, "0"),
                         Map.entry(IFernflowerPreferences.ASCII_STRING_CHARACTERS, "1"),
                         Map.entry(IFernflowerPreferences.BOOLEAN_TRUE_ONE, "0"),
                         Map.entry(IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT, "1"),
                         Map.entry(IFernflowerPreferences.USE_DEBUG_VAR_NAMES, "1"),
                         Map.entry(IFernflowerPreferences.USE_METHOD_PARAMETERS, "1"),
                         Map.entry(IFernflowerPreferences.REMOVE_EMPTY_RANGES, "1"),
                         Map.entry(IFernflowerPreferences.FINALLY_DEINLINE, "1"),
                         Map.entry(IFernflowerPreferences.RENAME_ENTITIES, "0"),
                         Map.entry(IFernflowerPreferences.IDEA_NOT_NULL_ANNOTATION, "1"),
                         Map.entry(IFernflowerPreferences.LAMBDA_TO_ANONYMOUS_CLASS, "0"),
                         Map.entry(IFernflowerPreferences.CONVERT_RECORD_PATTERN, "1"),
                         Map.entry(IFernflowerPreferences.CONVERT_PATTERN_SWITCH, "0"));
  }

  @Test
  public void testEnumLowLevel() { doTest("pkg/TestEnumLowLevel"); }
}
