// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.vfs

import com.intellij.ide.rpc.deserializeFromRpc
import com.intellij.ide.rpc.serializeToRpc
import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.vfs.VirtualFile
import fleet.util.openmap.SerializedValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

private val LOG = fileLogger()

/**
 * Converts an [VirtualFile] instance into a [VirtualFileId] which can be used in RPC calls and stored in Rhizome.
 */
@ApiStatus.Internal
fun VirtualFile.rpcId(): VirtualFileId {
  val file = this
  val serializedFile = serializeToRpc(file)
  return VirtualFileId(serializedFile, file)
}

/**
 * Retrieves the [VirtualFile] associated with the given [VirtualFileId].
 */
@ApiStatus.Internal
fun VirtualFileId.virtualFile(): VirtualFile? {
  if (localVirtualFile != null) {
    return localVirtualFile
  }

  return deserializeFromRpc<VirtualFile>(serializedValue) ?: run {
    LOG.error("Cannot deserialize VirtualFileRpc: $this")
    null
  }
}

@ApiStatus.Internal
@Serializable
class VirtualFileId internal constructor(@Serializable internal val serializedValue: SerializedValue? = null, @Transient internal val localVirtualFile: VirtualFile? = null)