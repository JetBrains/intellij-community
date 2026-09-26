// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.dsl

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.util.text.StringUtil
import org.intellij.lang.annotations.Language
import training.ui.LearningUiManager
import training.util.replaceSpacesWithNonBreakSpace
import training.util.surroundWithNonBreakSpaces
import javax.swing.Icon

/* Here can be defined common methods for any DSL level */
interface LearningDslBase {
  /** Show shortcut for [actionId] inside lesson step message */
  fun action(@Language("devkit-action-id") actionId: String): String {
    return "<action>$actionId</action>".surroundWithNonBreakSpaces()
  }

  /** Highlight as code inside lesson step message */
  fun code(sourceSample: String): String {
    return "<code>${StringUtil.escapeXmlEntities(sourceSample).replaceSpacesWithNonBreakSpace()}</code>".surroundWithNonBreakSpaces()
  }

  /** Highlight some [text] */
  fun strong(text: String): String {
    return "<strong>${StringUtil.escapeXmlEntities(text)}</strong>"
  }

  /** Show an [icon] inside lesson step message */
  fun icon(icon: Icon): String {
    val index = LearningUiManager.getIconIndex(icon)
    return "<icon_idx>$index</icon_idx>"
  }

  /** Show an icon from action widh [actionId] ID inside lesson step message */
  fun actionIcon(@Language("devkit-action-id") actionId: String): String {
    val icon = ActionManager.getInstance().getAction(actionId)?.templatePresentation?.icon ?: AllIcons.Toolbar.Unknown
    val index = LearningUiManager.getIconIndex(icon)
    return "<icon_idx>$index</icon_idx>"
  }

  fun shortcut(key: String): String {
    return "<shortcut>${key}</shortcut>".surroundWithNonBreakSpaces()
  }
}
