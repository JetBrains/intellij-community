// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.groovy.live.templates

import groovy.lang.GroovyClassLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GroovyScriptMacroTest {
  @Test
  fun `creates shell when language classloader cannot load Groovy runtime`() {
    val shell = GroovyScriptMacro.createGroovyShell(NoGroovyClassLoader())

    assertThat(shell.evaluate("return 'ok'")).isEqualTo("ok")
  }

  @Test
  fun `created shell uses language classloader as fallback`() {
    val languageClassLoader = GroovyClassLoader(GroovyScriptMacro::class.java.classLoader)
    languageClassLoader.parseClass("package scriptdeps; class ExternalValue { static String value() { 'fromLanguageClassLoader' } }")
    val shell = GroovyScriptMacro.createGroovyShell(languageClassLoader)

    val value = shell.evaluate(
      "return Class.forName('scriptdeps.ExternalValue', true, this.class.classLoader).getMethod('value').invoke(null)",
    )
    assertThat(value).isEqualTo("fromLanguageClassLoader")
  }

  private class NoGroovyClassLoader : ClassLoader(getPlatformClassLoader()) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
      if (name.startsWith("groovy.") || name.startsWith("org.codehaus.groovy.")) {
        throw ClassNotFoundException(name)
      }
      return super.loadClass(name, resolve)
    }
  }
}
