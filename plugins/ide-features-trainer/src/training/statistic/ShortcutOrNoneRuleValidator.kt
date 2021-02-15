// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.statistic

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.ui.KeyStrokeAdapter
import training.statistic.FeatureUsageStatisticConsts.SHORTCUT_RULE
import javax.swing.KeyStroke

class ShortcutOrNoneRuleValidator : CustomValidationRule() {
  override fun acceptRuleId(ruleId: String?): Boolean = (ruleId == SHORTCUT_RULE)

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    if (data == "none") return ValidationResultType.ACCEPTED
    val shortcut = parseKeyboardShortcut(data)
    if (shortcut != null) {
      return ValidationResultType.ACCEPTED
    }
    return ValidationResultType.REJECTED
  }

  private fun parseKeyboardShortcut(s: String): Shortcut? {
    val sc = s.replace("+", "").split(",").toTypedArray()
    if (sc.size > 2) return null
    val fst = KeyStrokeAdapter.getKeyStroke(sc[0]) ?: return null
    var snd: KeyStroke? = null
    if (sc.size == 2) {
      snd = KeyStrokeAdapter.getKeyStroke(sc[1])
    }
    return KeyboardShortcut(fst, snd)
  }
}