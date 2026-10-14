// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.encoding

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.jdom.Element
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class EncodingProjectStateTest {
  companion object {
    private val project = projectFixture()
  }

  @Test
  fun `an empty state clears a previously loaded project charset`() {
    val component = EncodingProjectManagerImpl(project.get())
    component.loadState(Element("component").addContent(Element("file").setAttribute("url", "PROJECT").setAttribute("charset", "UTF-16")))
    assertEquals(1, component.state!!.getChildren("file").size)
    component.loadState(Element("component"))
    assertTrue(component.state!!.getChildren("file").isEmpty())
  }
}
