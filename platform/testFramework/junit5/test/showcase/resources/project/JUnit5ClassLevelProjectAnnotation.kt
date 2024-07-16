// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase.resources.project

import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.resources.ProjectResource
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test


/**
 * Getting a project created automatically on class-level
 */
@TestApplication
@ProjectResource
class JUnit5ClassLevelProjectAnnotation {

  @Test
  fun test(project: Project) {
    assertFalse(project.isDisposed)
  }
}