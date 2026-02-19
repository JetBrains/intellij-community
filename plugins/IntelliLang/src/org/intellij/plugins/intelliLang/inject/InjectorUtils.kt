package org.intellij.plugins.intelliLang.inject

import com.intellij.lang.injection.MultiHostRegistrar

fun MultiHostRegistrar.registerSupport(support: LanguageInjectionSupport, settingsAvailable: Boolean): MultiHostRegistrar = also {
  InjectorUtils.registerSupport(it, support, settingsAvailable)
}
