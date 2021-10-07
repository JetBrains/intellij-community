package com.intellij.openapi.editor.colors.impl

import com.intellij.util.messages.Topic

interface EditorColorsManagerListener {
  fun schemesReloaded()

  companion object {
    @JvmField
    val TOPIC = Topic(EditorColorsManagerListener::class.java, Topic.BroadcastDirection.TO_DIRECT_CHILDREN, true)
  }
}