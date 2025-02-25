// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.document

import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.platform.pasta.common.DocumentEntity
import com.jetbrains.rhizomedb.exists
import fleet.kernel.change
import fleet.kernel.shared
import fleet.util.UID
import org.jetbrains.annotations.ApiStatus.Experimental
import java.util.*


@Experimental
interface DocumentEntityProvider {
  fun canCreateEntity(file: VirtualFile, document: DocumentEx): Boolean
  suspend fun createEntity(file: VirtualFile, document: DocumentEx): DocumentEntity
  suspend fun deleteEntity(entity: DocumentEntity)

  companion object {
    private val EP_NAME: ExtensionPointName<DocumentEntityProvider> = ExtensionPointName.create("com.intellij.documentEntityProvider")

    fun fileUID(file: VirtualFileWithId): UID {
      return fileUID(file.id)
    }

    fun fileUID(fileId: Int): UID {
      val virtualFileId = fileId.toString().toByteArray()
      val uuidStr = UUID.nameUUIDFromBytes(virtualFileId).toString()
      return UID.fromString(uuidStr)
    }

    internal fun getInstance(): DocumentEntityProvider {
      val providers = EP_NAME.extensionList
      return when (providers.size) {
        0 -> throw IllegalStateException("DefaultDocumentEntityProvider not found")
        1 -> providers[0]
        2 -> if (providers[0] is DefaultDocumentEntityProvider) {
          providers[1] // prioritise not default
        } else {
          providers[0]
        }
        else -> throw IllegalStateException("multiple DocumentEntityProvider found: $providers")
      }
    }
  }
}


private class DefaultDocumentEntityProvider() : DocumentEntityProvider {

  override fun canCreateEntity(file: VirtualFile, document: DocumentEx): Boolean {
    return file is VirtualFileWithId
  }

  override suspend fun createEntity(file: VirtualFile, document: DocumentEx): DocumentEntity {
    val uid = DocumentEntityProvider.fileUID(file as VirtualFileWithId)
    AdDocumentSynchronizer.getInstance()
    // TODO: data race?
    return change {
      shared {
        DocumentEntity.fromText(uid, document.immutableCharSequence)
      }
    }
  }

  override suspend fun deleteEntity(entity: DocumentEntity) {
    change {
      shared {
        if (entity.exists()) {
          entity.delete()
        }
      }
    }
  }
}
