// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang

import com.intellij.openapi.project.Project
import com.intellij.ui.ReferenceEditorWithBrowseButton
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import org.intellij.plugins.intelliLang.util.PsiUtilEx
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class AdvancedSettingsPanel(project: Project, configuration: Configuration.AdvancedConfiguration) {

  private val annotationField = ReferenceEditorWithBrowseButton(null, project, { s: String? -> PsiUtilEx.createDocument(s, project) }, configuration.languageAnnotationClass)
    .init(project)

  private val patternField = ReferenceEditorWithBrowseButton(null, project, { s: String? -> PsiUtilEx.createDocument(s, project) }, configuration.patternAnnotationClass)
    .init(project)

  private val substField = ReferenceEditorWithBrowseButton(null, project, { s: String? -> PsiUtilEx.createDocument(s, project) }, configuration.substAnnotationClass)
    .init(project)

  @JvmField
  val content = panel {
    group(IntelliLangBundle.message("settings.annotation.classes")) {
      row {
        cell(annotationField)
          .label(IntelliLangBundle.message("settings.language.annotation.class"), LabelPosition.TOP)
          .align(AlignX.FILL)
          .bindText(configuration::getLanguageAnnotationClass, configuration::setLanguageAnnotation)
      }
      row {
        cell(patternField)
          .label(IntelliLangBundle.message("settings.pattern.annotation.class"), LabelPosition.TOP)
          .align(AlignX.FILL)
          .bindText(configuration::getPatternAnnotationClass, configuration::setPatternAnnotation)
      }
      row {
        cell(substField)
          .label(IntelliLangBundle.message("settings.substitution.annotation.class"), LabelPosition.TOP)
          .align(AlignX.FILL)
          .bindText(configuration::getSubstAnnotationClass, configuration::setSubstAnnotation)
      }
    }

    group(IntelliLangBundle.message("settings.runtime.pattern.validation")) {
      buttonsGroup {
        row {
          radioButton(IntelliLangBundle.message("settings.no.runtime.instrumentation"), Configuration.InstrumentationType.NONE)
        }
        row {
          radioButton(IntelliLangBundle.message("settings.instrument.with.assertions"), Configuration.InstrumentationType.ASSERT)
        }
        row {
          radioButton(IntelliLangBundle.message("settings.instrument.with.illegal.argument.exception"), Configuration.InstrumentationType.EXCEPTION)
        }
      }.bind(configuration::getInstrumentation, configuration::setInstrumentationType)
    }

    group(IntelliLangBundle.message("settings.performance")) {
      buttonsGroup {
        row {
          radioButton(IntelliLangBundle.message("settings.do.not.analyze.anything"), Configuration.DfaOption.OFF)
        }
        row {
          radioButton(IntelliLangBundle.message("settings.analyze.references"), Configuration.DfaOption.RESOLVE)
        }
        row {
          radioButton(IntelliLangBundle.message("settings.look.for.variable.assignments"), Configuration.DfaOption.ASSIGNMENTS)
        }
        row {
          radioButton(IntelliLangBundle.message("settings.use.dataflow.analysis"), Configuration.DfaOption.DFA)
        }
      }.bind(configuration::getDfaOption, configuration::setDfaOption)
    }

    row {
      checkBox(IntelliLangBundle.message("settings.convert.undefined.operands.to.text"))
        .bindSelected(configuration::isIncludeUncomputablesAsLiterals, configuration::setIncludeUncomputablesAsLiterals)
    }
    row {
      checkBox(IntelliLangBundle.message("settings.add.language.annotation.or.comment"))
        .bindSelected(configuration::isSourceModificationAllowed, configuration::setSourceModificationAllowed)
    }
  }
}

private fun Cell<ReferenceEditorWithBrowseButton>.bindText(getter: () -> String, setter: (value: String) -> Unit) {
  bind(ReferenceEditorWithBrowseButton::getText, ReferenceEditorWithBrowseButton::setText, MutableProperty(getter, setter))
}

private fun ReferenceEditorWithBrowseButton.init(project: Project): ReferenceEditorWithBrowseButton {
  addActionListener(AdvancedSettingsUI.BrowseClassListener(project, this))
  isEnabled = !project.isDefault
  putClientProperty(DslComponentProperty.VISUAL_PADDINGS, UnscaledGaps(left = 2))

  return this
}