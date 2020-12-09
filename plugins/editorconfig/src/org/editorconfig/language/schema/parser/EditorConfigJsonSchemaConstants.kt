// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.parser

object EditorConfigJsonSchemaConstants {
  // Std types
  const val NUMBER = "number"
  const val STRING = "string"
  const val TEXT = "text"

  // Simple types
  const val CONST = "constant"
  const val DECLARATION = "declaration"
  const val REFERENCE = "reference"

  // Composite types
  const val PAIR = "pair"          // "true:warning"
  const val UNION = "union"        // "one", "two" - either one of given variants
  const val LIST = "list"          // "one, two" - enumeration
  const val OPTION = "option"      // "charset=utf-8"
  const val QUALIFIED = "qualified"  // "dotnet_naming_rule.public_members_must_be_capitalized.severity"

  // Properties
  const val TYPE = "type"
  const val VALUE = "value"
  const val VALUES = "values"
  const val DOCUMENTATION = "documentation"
  const val DEPRECATION = "deprecated"
  const val FIRST = "first"
  const val SECOND = "second"
  const val KEY = "key"
  const val MIN_LENGTH = "min_length"
  const val ALLOW_REPETITIONS = "allow_repetitions"
  const val ID = "id"
  const val NEEDS_REFERENCES = "needs_references"
  const val REQUIRED = "required"
  const val TYPE_ALIAS = "type_alias"
}
