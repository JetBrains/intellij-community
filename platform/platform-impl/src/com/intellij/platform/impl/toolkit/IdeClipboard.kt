// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.impl.toolkit

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.Disposer
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.FlavorEvent
import java.awt.datatransfer.FlavorListener
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException

// redirection from AWT API to CopyPasteManager (OS-level clipboard isn't used in a remote development host process)
internal class IdeClipboard : Clipboard("System") {
  private val flavorListeners = mutableMapOf<FlavorListener, Disposable>()

  private fun delegateToCopyPasteManager() = ApplicationManager.getApplication() != null && !ClientId.isCurrentlyUnderLocalId

  override fun setContents(contents: Transferable, owner: ClipboardOwner?) {
    if (delegateToCopyPasteManager()) {
      val copyPasteManager = CopyPasteManager.getInstance()
      copyPasteManager.setContents(contents)
      if (owner != null) {
        val disposable = Disposer.newDisposable("IdeClipboard.setContents")
        copyPasteManager.addContentChangedListener({ _, _ ->
                                                     Disposer.dispose(disposable)
                                                     owner.lostOwnership(this@IdeClipboard, contents)
                                                   }, disposable)
      }
    }
    else {
      super.setContents(contents, owner)
    }
  }

  override fun getContents(requestor: Any?): Transferable? {
    if (delegateToCopyPasteManager()) {
      return CopyPasteManager.getInstance().contents
    }
    else {
      return super.getContents(requestor)
    }
  }

  override fun getAvailableDataFlavors(): Array<DataFlavor> {
    if (delegateToCopyPasteManager()) {
      return CopyPasteManager.getInstance().contents?.transferDataFlavors ?: emptyArray()
    }
    else {
      return super.getAvailableDataFlavors()
    }
  }

  override fun isDataFlavorAvailable(flavor: DataFlavor): Boolean {
    if (delegateToCopyPasteManager()) {
      return CopyPasteManager.getInstance().areDataFlavorsAvailable(flavor)
    }
    else {
      return super.isDataFlavorAvailable(flavor)
    }
  }

  override fun getData(flavor: DataFlavor): Any {
    if (delegateToCopyPasteManager()) {
      return CopyPasteManager.getInstance().getContents(flavor) ?: throw UnsupportedFlavorException(flavor)
    }
    else {
      return super.getData(flavor)
    }
  }

  override fun addFlavorListener(listener: FlavorListener?) {
    if (delegateToCopyPasteManager()) {
      synchronized(flavorListeners) {
        if (listener != null && !flavorListeners.containsKey(listener)) {
          val disposable = Disposer.newDisposable("IdeClipboard.addFlavorListener")
          CopyPasteManager.getInstance().addContentChangedListener({ _, _ -> listener.flavorsChanged(FlavorEvent(this@IdeClipboard)) },
                                                                   disposable)
          flavorListeners[listener] = disposable
        }
      }
    }
    else {
      super.addFlavorListener(listener)
    }
  }

  override fun removeFlavorListener(listener: FlavorListener?) {
    if (delegateToCopyPasteManager()) {
      synchronized(flavorListeners) {
        flavorListeners.remove(listener)?.let { Disposer.dispose(it) }
      }
    }
    else {
      super.removeFlavorListener(listener)
    }
  }

  override fun getFlavorListeners(): Array<FlavorListener> {
    if (delegateToCopyPasteManager()) {
      synchronized(flavorListeners) {
        return flavorListeners.keys.toTypedArray()
      }
    }
    else {
      return super.getFlavorListeners()
    }
  }
}