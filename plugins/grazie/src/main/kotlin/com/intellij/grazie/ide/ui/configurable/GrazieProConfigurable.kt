package com.intellij.grazie.ide.ui.configurable

import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

@Suppress("MemberVisibilityCanBePrivate", "UnstableApiUsage")
class GrazieProConfigurable : BoundConfigurable(
  GrazieBundle.message("grazie.settings.configurable.name"),
  null
), SearchableConfigurable {

  private val config get() = GrazieConfig.get()

  var autoFix: Boolean
    get() = config.autoFix
    set(value) = GrazieConfig.update { it.copy(autoFix = value) }

  private val component: DialogPanel by lazy {
    panel {
      generalSettings()
    }
  }

  private fun Panel.generalSettings(): Row {
    return group {
      row {
        checkBox(GrazieBundle.message("grazie.settings.auto.apply.fixes.label")).bindSelected(::autoFix)
      }

      row {
        @Suppress("DialogTitleCapitalization")
        val oxfordCb = checkBox(GrazieBundle.message("grazie.settings.use.oxford.spelling.checkbox"))
          .bindSelected(
            getter = { config.useOxfordSpelling },
            setter = { GrazieConfig.update { state -> state.withOxfordSpelling(it) } }
          )

        fun updateAvailability() {
          oxfordCb.enabled(Lang.BRITISH_ENGLISH in GrazieConfig.get().availableLanguages)
        }
        updateAvailability()
        GrazieConfig.subscribe(disposable!!) { updateAvailability() }
      }
    }
  }

  override fun createPanel(): DialogPanel = component

  override fun getId(): String = ID

  companion object {
    internal const val ID = "reference.settings.grazie.pro.in.ai.assistant"
  }
}
