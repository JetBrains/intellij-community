// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GroovyEditorActions")

package org.jetbrains.plugins.groovy.lang.actions

import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.annotations.TestOnly

/**
 * Performs the editor action [actionId] in [fixture] and removes the trailing spaces of the document.
 * It replaces the JUnit 3 base `GroovyEditorActionTestBase`. Call it on the EDT.
 */
@TestOnly
fun performAction(fixture: JavaCodeInsightTestFixture, actionId: String) {
  fixture.performEditorAction(actionId)
  (fixture.editor.document as DocumentImpl).stripTrailingSpaces(fixture.project)
}
