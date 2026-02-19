package com.intellij.openapi.editor.colors.impl

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface EditorColorsManagerListener {
  fun schemesReloaded()

  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<EditorColorsManagerListener> = Topic(EditorColorsManagerListener::class.java, Topic.BroadcastDirection.TO_DIRECT_CHILDREN, true)
  }
}