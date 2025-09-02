// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.parser

object EditorConfigJsonSchemaConstants {
  // Std types
  const val NUMBER: String = "number"
  const val STRING: String = "string"
  const val TEXT: String = "text"

  // Simple types
  const val CONST: String = "constant"
  const val DECLARATION: String = "declaration"
  const val REFERENCE: String = "reference"

  // Composite types
  const val PAIR: String = "pair"          // "true:warning"
  const val UNION: String = "union"        // "one", "two" - either one of given variants
  const val LIST: String = "list"          // "one, two" - enumeration
  const val OPTION: String = "option"      // "charset=utf-8"
  const val QUALIFIED: String = "qualified"  // "dotnet_naming_rule.public_members_must_be_capitalized.severity"

  // Properties
  const val TYPE: String = "type"
  const val VALUE: String = "value"
  const val VALUES: String = "values"
  const val DOCUMENTATION: String = "documentation"
  const val DEPRECATION: String = "deprecated"
  const val FIRST: String = "first"
  const val SECOND: String = "second"
  const val KEY: String = "key"
  const val MIN_LENGTH: String = "min_length"
  const val ALLOW_REPETITIONS: String = "allow_repetitions"
  const val ID: String = "id"
  const val NEEDS_REFERENCES: String = "needs_references"
  const val REQUIRED: String = "required"
  const val TYPE_ALIAS: String = "type_alias"
}
