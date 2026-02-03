// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
@ApiStatus.Experimental
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
 * This [EditorId] can be used for RPC calls between frontend and backend.
 *
 * **WARNING: This API is experimental and should be used with care.**
 *
 * This function retrieves the unique identifier assigned to an Editor instance, returning null if not found.
 * Important considerations:
 * - Returns null for editors that have not been properly initialized with an EditorId
 * - In Remote Development scenarios, ensures editor identity is maintained across frontend/backend boundaries
 * - Safe to use for RPC transmission as EditorId is serializable
 * - Safer alternative to [editorId] that doesn't throw exceptions
 *
 * @return The [EditorId] instance associated with the provided [Editor],
 *         or null if [Editor]'s implementation didn't assign id to it
 */
@ApiStatus.Experimental
fun Editor.editorIdOrNull(): EditorId? {
  return getUserData(KERNEL_EDITOR_ID_KEY)
}

/**
 * Provides [EditorId] for the given [Editor].
 * This [EditorId] can be used for RPC calls between frontend and backend.
 *
 * **WARNING: This API is experimental and should be used with care.**
 *
 * This function retrieves the unique identifier assigned to an Editor instance. Important considerations:
 * - Only works for editors that have been properly initialized with an EditorId
 * - In Remote Development scenarios, ensures editor identity is maintained across frontend/backend boundaries
 * - Safe to use for RPC transmission as EditorId is serializable
 * - Throws an exception if the editor doesn't have an assigned ID
 *
 * @return The [EditorId] instance associated with the provided [Editor]
 * @throws IllegalStateException if [Editor]'s implementation didn't assign [EditorId] to it
 */
@ApiStatus.Experimental
fun Editor.editorId(): EditorId {
  return editorIdOrNull() ?: error("EditorId is not found for editor: $this")
}

/**
 * Provides [Editor] for the given [EditorId].
 *
 * **WARNING: This API is experimental and should be used with care.**
 *
 * This function attempts to locate an Editor instance by its unique identifier. Important considerations:
 * - **Won't work for editors created on the frontend** - only editors that exist on the backend
 *   can be found using this method
 * - Only finds editors that are currently open and have been assigned the corresponding EditorId
 * - May return null if the editor was closed or disposed after the EditorId was obtained
 *
 * @return The [Editor] instance associated with the provided [EditorId],
 *         or null if there is no editor with the given [EditorId]
 */
@ApiStatus.Experimental
fun EditorId.findEditorOrNull(): Editor? {
  return EditorFactory.getInstance().allEditors.find { it.editorIdOrNull() == this }
}

/**
 * Provides [Editor] for the given [EditorId].
 *
 * **WARNING: This API is experimental and should be used with care.**
 *
 * This function attempts to locate an Editor instance by its unique identifier and throws an exception if not found.
 * Important considerations:
 * - **Won't work for editors created on the frontend** - only editors that exist on the backend
 *   can be found using this method
 * - Only finds editors that are currently open and have been assigned the corresponding EditorId
 * - Throws an exception if the editor was closed or disposed after the EditorId was obtained
 *
 * @return The [Editor] instance associated with the provided [EditorId]
 * @throws IllegalStateException if there is no editor with the given [EditorId]
 */
@ApiStatus.Experimental
fun EditorId.findEditor(): Editor {
  return findEditorOrNull() ?: error("Editor is not found for id: $this")
}

internal fun EditorImpl.putEditorId() {
  getOrCreateUserData(KERNEL_EDITOR_ID_KEY) { EditorId.create() }
}
