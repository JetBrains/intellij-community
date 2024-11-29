// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler

import com.intellij.application.options.CodeStyle
import com.intellij.ide.highlighter.JavaFileType
import org.jetbrains.annotations.Nls
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences

/**
 * Exposing decompilation presets to the users was initially discussed in IDEA-343826.
 */
internal enum class DecompilerPreset(@Nls val description: String, val options: Map<String, String>) {
  HIGH(IdeaDecompilerBundle.message("decompiler.preset.high.description"), highPreset),
  MEDIUM(IdeaDecompilerBundle.message("decompiler.preset.medium.description"), mediumPreset),
  LOW(IdeaDecompilerBundle.message("decompiler.preset.low.description"), lowPreset);

  fun toCommandLineInvocation(): String {
    return options
      .filterNot { (key, _) -> key == "ban" || key == "ind" } // remove banner and indent flags as they breaks output
      .map { (key, value) -> "-$key=$value" }.joinToString(separator = " ")
  }
}

private val basePreset: Map<String, String> = mapOf(
  // Appearance-specific options
  IFernflowerPreferences.BANNER to IDEA_DECOMPILER_BANNER,
  IFernflowerPreferences.NEW_LINE_SEPARATOR to "1",
  IFernflowerPreferences.INDENT_STRING to " ".repeat(CodeStyle.getDefaultSettings().getIndentOptions(JavaFileType.INSTANCE).INDENT_SIZE),

  IFernflowerPreferences.MAX_PROCESSING_METHOD to "60",
  IFernflowerPreferences.IGNORE_INVALID_BYTECODE to "1",
  IFernflowerPreferences.VERIFY_ANONYMOUS_CLASSES to "1",
  IFernflowerPreferences.CHECK_CLOSABLE_INTERFACE to "0", // Must be 0, otherwise it doesn't work in IDEA

  // If you're debugging, it might be useful to uncomment this:
  // IFernflowerPreferences.UNIT_TEST_MODE to if (ApplicationManager.getApplication().isUnitTestMode) "1" else "0"
)

private val highPreset: Map<String, String> = basePreset + mapOf(
  IFernflowerPreferences.REMOVE_BRIDGE to "1",
  IFernflowerPreferences.REMOVE_SYNTHETIC to "1",
  IFernflowerPreferences.DECOMPILE_INNER to "1",
  IFernflowerPreferences.DECOMPILE_CLASS_1_4 to "1",
  IFernflowerPreferences.DECOMPILE_ASSERTIONS to "1",
  IFernflowerPreferences.HIDE_EMPTY_SUPER to "1",
  IFernflowerPreferences.HIDE_DEFAULT_CONSTRUCTOR to "1",
  IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES to "1",
  IFernflowerPreferences.NO_EXCEPTIONS_RETURN to "1",
  IFernflowerPreferences.DECOMPILE_ENUM to "1",
  IFernflowerPreferences.REMOVE_GET_CLASS_NEW to "1",
  IFernflowerPreferences.LITERALS_AS_IS to "0",
  IFernflowerPreferences.ASCII_STRING_CHARACTERS to "1",
  IFernflowerPreferences.BOOLEAN_TRUE_ONE to "1",
  IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT to "1",
  IFernflowerPreferences.USE_DEBUG_VAR_NAMES to "1",
  IFernflowerPreferences.USE_METHOD_PARAMETERS to "1",
  IFernflowerPreferences.REMOVE_EMPTY_RANGES to "1",
  IFernflowerPreferences.FINALLY_DEINLINE to "1",
  IFernflowerPreferences.RENAME_ENTITIES to "0",
  IFernflowerPreferences.IDEA_NOT_NULL_ANNOTATION to "1",
  IFernflowerPreferences.LAMBDA_TO_ANONYMOUS_CLASS to "0",
  IFernflowerPreferences.CONVERT_RECORD_PATTERN to "1",
  IFernflowerPreferences.CONVERT_PATTERN_SWITCH to "1",
  IFernflowerPreferences.HIDE_RECORD_CONSTRUCTOR_AND_GETTERS to "1",
)

private val mediumPreset: Map<String, String> = basePreset + mapOf(
  IFernflowerPreferences.REMOVE_BRIDGE to "0",
  IFernflowerPreferences.REMOVE_SYNTHETIC to "0",
  IFernflowerPreferences.DECOMPILE_INNER to "1",
  IFernflowerPreferences.DECOMPILE_CLASS_1_4 to "1",
  IFernflowerPreferences.DECOMPILE_ASSERTIONS to "1",
  IFernflowerPreferences.HIDE_EMPTY_SUPER to "0",
  IFernflowerPreferences.HIDE_DEFAULT_CONSTRUCTOR to "0",
  IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES to "1",
  IFernflowerPreferences.NO_EXCEPTIONS_RETURN to "1",
  IFernflowerPreferences.DECOMPILE_ENUM to "1",
  IFernflowerPreferences.REMOVE_GET_CLASS_NEW to "1",
  IFernflowerPreferences.LITERALS_AS_IS to "0",
  IFernflowerPreferences.ASCII_STRING_CHARACTERS to "1",
  IFernflowerPreferences.BOOLEAN_TRUE_ONE to "1",
  IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT to "1",
  IFernflowerPreferences.USE_DEBUG_VAR_NAMES to "1",
  IFernflowerPreferences.USE_METHOD_PARAMETERS to "1",
  IFernflowerPreferences.REMOVE_EMPTY_RANGES to "1",
  IFernflowerPreferences.FINALLY_DEINLINE to "1",
  IFernflowerPreferences.RENAME_ENTITIES to "0",
  IFernflowerPreferences.IDEA_NOT_NULL_ANNOTATION to "1",
  IFernflowerPreferences.LAMBDA_TO_ANONYMOUS_CLASS to "0",
  IFernflowerPreferences.CONVERT_RECORD_PATTERN to "1",
  IFernflowerPreferences.CONVERT_PATTERN_SWITCH to "1",
  IFernflowerPreferences.HIDE_RECORD_CONSTRUCTOR_AND_GETTERS to "0",
)

private val lowPreset: Map<String, String> = basePreset + mapOf(
  IFernflowerPreferences.REMOVE_BRIDGE to "0",
  IFernflowerPreferences.REMOVE_SYNTHETIC to "0",
  IFernflowerPreferences.DECOMPILE_INNER to "1",
  IFernflowerPreferences.DECOMPILE_CLASS_1_4 to "1",
  IFernflowerPreferences.DECOMPILE_ASSERTIONS to "0",
  IFernflowerPreferences.HIDE_EMPTY_SUPER to "0",
  IFernflowerPreferences.HIDE_DEFAULT_CONSTRUCTOR to "0",
  IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES to "1",
  IFernflowerPreferences.NO_EXCEPTIONS_RETURN to "1",
  IFernflowerPreferences.DECOMPILE_ENUM to "0",
  IFernflowerPreferences.REMOVE_GET_CLASS_NEW to "0",
  IFernflowerPreferences.LITERALS_AS_IS to "1",
  IFernflowerPreferences.ASCII_STRING_CHARACTERS to "1",
  IFernflowerPreferences.BOOLEAN_TRUE_ONE to "0",
  IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT to "1",
  IFernflowerPreferences.USE_DEBUG_VAR_NAMES to "1",
  IFernflowerPreferences.USE_METHOD_PARAMETERS to "1",
  IFernflowerPreferences.REMOVE_EMPTY_RANGES to "1",
  IFernflowerPreferences.FINALLY_DEINLINE to "1",
  IFernflowerPreferences.RENAME_ENTITIES to "0",
  IFernflowerPreferences.IDEA_NOT_NULL_ANNOTATION to "1",
  IFernflowerPreferences.LAMBDA_TO_ANONYMOUS_CLASS to "0",
  IFernflowerPreferences.CONVERT_RECORD_PATTERN to "0",
  IFernflowerPreferences.CONVERT_PATTERN_SWITCH to "0",
  IFernflowerPreferences.HIDE_RECORD_CONSTRUCTOR_AND_GETTERS to "0",
)
