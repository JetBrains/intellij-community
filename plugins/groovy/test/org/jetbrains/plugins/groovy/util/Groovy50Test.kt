// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.util

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors

abstract class Groovy50Test : LightProjectTest() {
  override fun getProjectDescriptor(): LightProjectDescriptor? {
    return GroovyProjectDescriptors.GROOVY_5_0
  }
}