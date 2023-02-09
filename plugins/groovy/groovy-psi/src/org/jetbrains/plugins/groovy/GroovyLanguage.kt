// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy

import com.intellij.lang.Language
import com.intellij.lang.jvm.JvmLanguage

/**
 * All main properties for Groovy language
 */
object GroovyLanguage : Language("Groovy"), JvmLanguage {

  override fun isCaseSensitive(): Boolean = true
}
