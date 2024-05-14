// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase.resources.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.resources.FullApplication
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Inject project in a static field: one project for the whole class
 */
@FullApplication
class JUnit5ProjectClassField {
  companion object {
    lateinit var project: Project
  }

  @Test
  fun ensureProject() {
    Assertions.assertFalse(project.isDisposed)
    Disposer.register(project) {

    }
  }
}