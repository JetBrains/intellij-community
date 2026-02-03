// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.encoding

import com.intellij.openapi.editor.Document
import com.intellij.testFramework.HeavyPlatformTestCase

class DefaultEncodingListenerTest : HeavyPlatformTestCase() {
  private inner class EncodingChangeListener : EncodingManagerListener {
    var eventsFired: Int = 0

    override fun propertyChanged(document: Document?, propertyName: String, oldValue: Any?, newValue: Any?) {
      if (propertyName == EncodingManager.PROP_DEFAULT_FILES_ENCODING) {
        ++eventsFired
      }
    }
  }

  fun testDefaultEncodingChangeFiresEvent() {
    val encodingChangeListener = EncodingChangeListener()

    project.messageBus.connect(testRootDisposable).subscribe(EncodingManagerListener.ENCODING_MANAGER_CHANGES, encodingChangeListener)

    val encodingProjectManager = EncodingProjectManager.getInstance(project)
    encodingProjectManager.defaultCharsetName = "windows-1251"

    assertEquals("Expected PROP_DEFAULT_FILES_ENCODING to be fired", 1, encodingChangeListener.eventsFired)

    encodingProjectManager.defaultCharsetName = "invalid-charset"
    assertEquals("Expected PROP_DEFAULT_FILES_ENCODING to be fired", 2, encodingChangeListener.eventsFired)
  }

  fun testSettingSameValueDoesNotFireEvent() {
    val encodingChangeListener = EncodingChangeListener()

    project.messageBus.connect(testRootDisposable).subscribe(EncodingManagerListener.ENCODING_MANAGER_CHANGES, encodingChangeListener)

    val encodingProjectManager = EncodingProjectManager.getInstance(project)
    encodingProjectManager.defaultCharsetName = encodingProjectManager.defaultCharsetName

    assertEquals("Setting the same value should not fire an event", 0, encodingChangeListener.eventsFired)
  }
}