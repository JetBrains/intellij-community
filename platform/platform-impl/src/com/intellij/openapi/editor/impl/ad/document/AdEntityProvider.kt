// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.document

import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.ad.markup.AdMarkupEntity
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.platform.pasta.common.DocumentEntity
import fleet.util.UID
import java.util.UUID


interface AdEntityProvider {

  fun getDocEntityUid(document: DocumentEx): UID?
  suspend fun createDocEntity(uid: UID, document: DocumentEx): DocumentEntity
  suspend fun deleteDocEntity(docEntity: DocumentEntity)
  suspend fun createDocMarkupEntity(uid: UID, markupModel: MarkupModelEx): AdMarkupEntity
  suspend fun deleteDocMarkupEntity(markupEntity: AdMarkupEntity)

  companion object {
    private val EP_NAME: ExtensionPointName<AdEntityProvider> = ExtensionPointName.create("com.intellij.adEntityProvider")

    fun fileUID(file: VirtualFileWithId): UID {
      return fileUID(file.id)
    }

    fun fileUID(fileId: Int): UID {
      val virtualFileId = fileId.toString().toByteArray()
      val uuidStr = UUID.nameUUIDFromBytes(virtualFileId).toString()
      return UID.fromString(uuidStr)
    }

    internal fun getInstance(): AdEntityProvider {
      val providers = EP_NAME.extensionList
      return when (providers.size) {
        0 -> throw IllegalStateException("DefaultAdEntityProvider not found")
        1 -> providers[0]
        2 -> if (providers[0] is DefaultAdEntityProvider) {
          providers[1] // prioritise not default
        } else {
          providers[0]
        }
        else -> throw IllegalStateException("multiple AdEntityProvider found: $providers")
      }
    }
  }
}
