// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.elf

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.elf.Elf
import com.intellij.openapi.editor.ex.DocumentMagicCore
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts.Command
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.EDT

internal class ElfImpl : Elf {
  @Volatile private var inElfScope: Boolean = false
  @Volatile private var inCommand: Boolean = false
  // TODO: optimize me
  private val delayed: MutableCollection<Runnable> = ContainerUtil.createLockFreeCopyOnWriteList()

  override fun withElfScope(action: Runnable) {
    ThreadingAssertions.assertEventDispatchThread()
    val old = inElfScope
    inElfScope = true
    try {
      action.run()
    } finally {
      inElfScope = old
      if (!isInElfScope()) {
        performDelayedActions()
      }
    }
  }

  private fun performDelayedActions() {
    ThreadingAssertions.assertEventDispatchThread()
    val actions = delayed.toList()
    delayed.clear()
    for (onScopeFinished in actions) {
      onScopeFinished.run()
    }
  }

  override fun isInElfScope(): Boolean {
    return inElfScope && EDT.isCurrentThreadEdt()
  }

  override fun isPsiInteractionAllowed(): Boolean {
    return !isInElfScope() || isLockFreePsiSupported()
  }

  override fun getElfDocument(document: Document): Document {
    val existing = document.getUserData(ELF_DOCUMENT_KEY)
    if (existing != null) {
      return existing
    }
    if (document is DocumentImpl) {
      val core = document.core
      if (core is DocumentMagicCore) {
        val res = DocumentImpl(core.elfCore(), document)
        document.putUserData(ELF_DOCUMENT_KEY, res)
        return res
      }
    }
    return document
  }

  override fun getRealDocument(document: Document): Document {
    val existing = document.getUserData(REAL_DOCUMENT_KEY)
    if (existing != null) {
      return existing
    }
    if (document is DocumentImpl) {
      val core = document.core
      if (core is DocumentMagicCore) {
        val res = DocumentImpl(core.realCore(), document)
        document.putUserData(REAL_DOCUMENT_KEY, res)
        return res
      }
    }
    return document
  }

  override fun performOnScopeFinished(action: Runnable) {
    if (!isInElfScope()) {
      throw IllegalStateException("Scheduling delayed task is not allowed outside elf scope")
    }
    delayed.add(action)
  }

  override fun isElfCommandInProgress(): Boolean {
    return inCommand
  }

  override fun executeElfCommand(
    commandProject: Project?,
    commandName: @Command String?,
    commandGroupId: Any?,
    command: Runnable,
  ) {
    val oldVal = inCommand
    inCommand = true
    try {
      CommandProcessor.getInstance().executeCommand(
        commandProject,
        command,
        commandName,
        commandGroupId,
      )
    } finally {
      inCommand = oldVal
    }
  }

  private fun isLockFreePsiSupported(): Boolean {
    // TODO: integrate new mvcc psi
    return false
  }

  companion object {
    private val ELF_DOCUMENT_KEY: Key<Document> = Key.create("ELF_DOCUMENT_KEY")
    private val REAL_DOCUMENT_KEY: Key<Document> = Key.create("REAL_DOCUMENT_KEY")
  }
}
