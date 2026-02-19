// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.document

import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.ad.markup.AdMarkupEntity
import com.intellij.openapi.editor.impl.ad.markup.AdMarkupSynchronizerService
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.platform.pasta.common.DocumentEntity
import com.jetbrains.rhizomedb.exists
import fleet.kernel.change
import fleet.kernel.rebase.shared
import fleet.util.UID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import java.util.concurrent.ConcurrentHashMap


internal class DefaultAdEntityProvider : AdEntityProvider {
  private val docToScope= ConcurrentHashMap<DocumentEntity, CoroutineScope>()
  private val markupToScope= ConcurrentHashMap<AdMarkupEntity, CoroutineScope>()

  override fun getDocEntityUid(document: DocumentEx): UID? {
    val file = FileDocumentManager.getInstance().getFile(document)
    return if (file is VirtualFileWithId) AdEntityProvider.fileUID(file) else null
  }

  override suspend fun createDocEntity(uid: UID, document: DocumentEx): DocumentEntity {
    val coroutineScope = AdDocumentSynchronizer.getInstance().bindDocumentListener(document)
    val text = document.immutableCharSequence // TODO: data race between creation and synchronizer?
    val entity = change {
      shared {
        DocumentEntity.fromText(uid, text)
      }
    }
    docToScope[entity] = coroutineScope
    return entity
  }

  override suspend fun deleteDocEntity(docEntity: DocumentEntity) {
    docToScope.remove(docEntity)!!.cancel()
    change {
      shared {
        if (docEntity.exists()) {
          docEntity.delete()
        }
      }
    }
  }

  override suspend fun createDocMarkupEntity(uid: UID, markupModel: MarkupModelEx): AdMarkupEntity {
    val document = markupModel.document as? DocumentEx
                   ?: throw IllegalStateException("document is expected to be DocumentEx")
    val docEntity = AdDocumentManager.getInstance().getDocEntity(document)

    checkNotNull(docEntity) {
      // TODO: investigate why happens in vcs shift+ctrl+option
      //  clue - failed to create editor model EditorImpl[LightVirtualFile: /Dummy.txt]
      "doc entity not found"
    }

    val markupEntity = change {
      shared {
        AdMarkupEntity.empty(uid, docEntity)
      }
    }
    val cs = AdMarkupSynchronizerService.getInstance().createSynchronizer(markupEntity, markupModel)
    markupToScope[markupEntity] = cs
    return markupEntity
  }

  override suspend fun deleteDocMarkupEntity(markupEntity: AdMarkupEntity) {
    markupToScope.remove(markupEntity)!!.cancel()
    change {
      shared {
        if (markupEntity.exists()) {
          markupEntity.delete()
        }
      }
    }
  }
}
