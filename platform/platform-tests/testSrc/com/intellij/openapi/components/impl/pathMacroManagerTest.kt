// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl

import com.intellij.application.options.NamedPathMacroContributor
import com.intellij.application.options.PathMacrosImpl
import com.intellij.openapi.application.PathMacroContributor
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.util.SystemProperties
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.jetbrains.jps.model.serialization.PathMacroUtil
import org.junit.Rule
import org.junit.Test

private const val CONTRIBUTED_MACRO = "CONTRIBUTED_MACRO"

class LightPathMacroManagerTest {
  @get: Rule
  val app: ApplicationRule = ApplicationRule()

  @get:Rule
  val disposableRule: DisposableRule = DisposableRule()

  @Test
  fun systemOverridesUser() {
    val macros = PathMacrosImpl()
    val userHome = FileUtil.toSystemIndependentName(SystemProperties.getUserHome())
    macros.setMacro("foo", userHome)

    val manager = PathMacroManager(macros)
    assertThat(manager.collapsePath(userHome)).isEqualTo("$${PathMacroUtil.USER_HOME_NAME}$")
  }

  @Test
  fun namedContributorIsNotAskedForStoredMacro() {
    val contributor = NamedCountingContributor()
    registerContributor(contributor)

    val macros = PathMacrosImpl()
    macros.loadState(JDOMUtil.load("""<state><macro name="$CONTRIBUTED_MACRO" value="/stored"/></state>"""))

    assertThat(contributor.registrationCount).isZero()
    assertThat(macros.getValue(CONTRIBUTED_MACRO)).isEqualTo("/stored")
  }

  @Test
  fun namedContributorRegistersMacroThatIsNotStored() {
    val contributor = NamedCountingContributor()
    registerContributor(contributor)

    val macros = PathMacrosImpl()
    macros.loadState(Element("state"))

    assertThat(contributor.registrationCount).isEqualTo(1)
    assertThat(macros.getValue(CONTRIBUTED_MACRO)).isEqualTo("/contributed")
  }

  @Test
  fun contributorWithoutNamesIsAskedAndStoredValueWins() {
    val contributor = CountingContributor()
    registerContributor(contributor)

    val macros = PathMacrosImpl()
    macros.loadState(JDOMUtil.load("""<state><macro name="$CONTRIBUTED_MACRO" value="/stored"/></state>"""))

    assertThat(contributor.registrationCount).isEqualTo(1)
    assertThat(macros.getValue(CONTRIBUTED_MACRO)).isEqualTo("/stored")
  }

  private fun registerContributor(contributor: PathMacroContributor) {
    ExtensionTestUtil.maskExtensions(
      ExtensionPointName<PathMacroContributor>("com.intellij.pathMacroContributor"),
      listOf(contributor),
      disposableRule.disposable,
      fireEvents = false,
    )
  }
}

private open class CountingContributor : PathMacroContributor {
  var registrationCount: Int = 0

  override fun registerPathMacros(macros: MutableMap<String, String>, legacyMacros: MutableMap<String, String>) {
    registrationCount++
    macros.put(CONTRIBUTED_MACRO, "/contributed")
  }
}

private class NamedCountingContributor : CountingContributor(), NamedPathMacroContributor {
  override val macroNames: Collection<String>
    get() = listOf(CONTRIBUTED_MACRO)
}
