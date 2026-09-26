// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch

import com.intellij.structuralsearch.groovy.GroovyStructuralSearchScriptEngine
import groovy.lang.GroovyClassLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GroovyStructuralSearchScriptEngineTest {
  @Test
  fun `creates shell when dialect classloader cannot load Groovy runtime`() {
    val shell = GroovyStructuralSearchScriptEngine.createGroovyShell(NoGroovyClassLoader())

    assertThat(shell.evaluate("return 'ok'")).isEqualTo("ok")
  }

  @Test
  fun `uses dialect classloader as fallback`() {
    val dialectClassLoader = GroovyClassLoader(GroovyStructuralSearchScriptEngine::class.java.classLoader)
    dialectClassLoader.parseClass("package scriptdeps; class ExternalValue { static String value() { 'fromDialectClassLoader' } }")
    val shell = GroovyStructuralSearchScriptEngine.createGroovyShell(dialectClassLoader)

    val value = shell.evaluate(
      "return Class.forName('scriptdeps.ExternalValue', true, this.class.classLoader).getMethod('value').invoke(null)",
    )
    assertThat(value).isEqualTo("fromDialectClassLoader")
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
