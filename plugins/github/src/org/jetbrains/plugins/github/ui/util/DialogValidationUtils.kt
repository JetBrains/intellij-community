// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.util

import com.intellij.openapi.ui.ValidationInfo
import javax.swing.JTextField

object DialogValidationUtils {
  /**
   * Returns [ValidationInfo] with [message] if [textField] is blank
   */
  fun notBlank(textField: JTextField, message: String): ValidationInfo? {
    return if (textField.text.isNullOrBlank()) ValidationInfo(message, textField) else null
  }

  /**
   * Chains the [validators] so that if one of them returns non-null [ValidationInfo] the rest of them are not checked
   */
  fun chain(vararg validators: Validator): Validator = { validators.asSequence().mapNotNull { it() }.firstOrNull() }

  /**
   * Stateful validator that checks that contents of [textField] are unique among [records]
   */
  class RecordUniqueValidator(private val textField: JTextField, private val message: String) : Validator {
    var records: Set<String> = setOf<String>()

    override fun invoke(): ValidationInfo? = if (records.contains(textField.text)) ValidationInfo(message, textField) else null
  }
}

typealias Validator = () -> ValidationInfo?