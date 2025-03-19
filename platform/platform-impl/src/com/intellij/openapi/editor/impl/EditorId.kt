// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserData
import fleet.util.UID
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Represents unique id for an [Editor].
 * This [EditorId] is shared by frontend and backend.
 *
 * @see editorId
 * @see findEditor
 */
@Serializable
@ApiStatus.Internal
data class EditorId(private val id: UID) {
  // NB: This API is necessary only for TextControlHost functionality that uses RD Protocol
  //     for RemoteApi use just [EditorId], since it is serializable
  @ApiStatus.Internal
  fun serializeToString(): String {
    return id.toString()
  }

  companion object {
    // NB: This API is necessary only for TextControlHost functionality that uses RD Protocol
    //     for RemoteApi use just [EditorId], since it is serializable
    @JvmStatic
    @ApiStatus.Internal
    fun deserializeFromString(value: String): EditorId {
      return EditorId(UID.fromString(value))
    }

    /**
     * Provides new [EditorId], which can be attached to an editor.
     * This factory function is necessary only for RD Protocol and shouldn't be used in the user side code.
     * Editors out of RD Protocol should already have attached [EditorId] by [EditorFactory].
     */
    @JvmStatic
    @ApiStatus.Internal
    fun create(): EditorId {
      return EditorId(UID.random())
    }
  }
}

@ApiStatus.Internal
val KERNEL_EDITOR_ID_KEY = Key.create<EditorId>("EditorImpl.KERNEL_EDITOR_ID")

/**
 * Provides [EditorId] for the given [Editor].
 * This [EditorId] can be used for RPC calls between frontend and backend
 *
 * @return The [EditorId] instance associated with the provided [Editor],
 *         or null if [Editor]'s implementation didn't assign id to it.
 */
@ApiStatus.Internal
fun Editor.editorIdOrNull(): EditorId? {
  return getUserData(KERNEL_EDITOR_ID_KEY)
}

/**
 * Provides [EditorId] for the given [Editor].
 * This [EditorId] can be used for RPC calls between frontend and backend
 *
 * @return The [EditorId] instance associated with the provided [Editor]
 * @throws IllegalStateException if [Editor]'s implementation didn't assign [EditorId] to it
 */
@ApiStatus.Internal
fun Editor.editorId(): EditorId {
  return editorIdOrNull() ?: error("EditorId is not found for editor: $this")
}

/**
 * Provides [Editor] for the given [EditorId].
 *
 * @return The [Editor] instance associated with the provided [EditorId],
 *         or null if there is no editor with the given [EditorId].
 */
@ApiStatus.Internal
fun EditorId.findEditorOrNull(): Editor? {
  return EditorFactory.getInstance().allEditors.find { it.editorIdOrNull() == this }
}

/**
 * Provides [Editor] for the given [EditorId].
 *
 * @return The [Editor] instance associated with the provided [EditorId],
 *         or null if there is no editor with the given [EditorId].
 * @throws IllegalStateException if there is no editor with the given [EditorId].
 */
@ApiStatus.Internal
fun EditorId.findEditor(): Editor {
  return findEditorOrNull() ?: error("Editor is not found for id: $this")
}

internal fun EditorImpl.putEditorId() {
  getOrCreateUserData(KERNEL_EDITOR_ID_KEY) { EditorId.create() }
}
