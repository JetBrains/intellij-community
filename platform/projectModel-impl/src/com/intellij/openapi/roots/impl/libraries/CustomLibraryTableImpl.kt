// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl.libraries

import com.intellij.openapi.Disposable
import com.intellij.openapi.roots.libraries.CustomLibraryTable
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablePresentation
import com.intellij.openapi.util.Disposer
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.CustomLibraryTableBridgeImpl
import com.intellij.workspaceModel.ide.legacyBridge.CustomLibraryTableBridge
import org.jdom.Element

class CustomLibraryTableImpl(level: String, presentation: LibraryTablePresentation) : CustomLibraryTable, Disposable {
  private val delegate: CustomLibraryTable = if (CustomLibraryTableBridge.isEnabled()) {
    CustomLibraryTableBridgeImpl(level, presentation)
  }
  else {
    LegacyCustomLibraryTable(level, presentation)
  }

  override fun getLibraries(): Array<Library> = delegate.libraries

  override fun createLibrary(): Library = delegate.createLibrary()

  override fun createLibrary(name: String?): Library = delegate.createLibrary(name)

  override fun removeLibrary(library: Library) = delegate.removeLibrary(library)

  override fun getLibraryIterator(): MutableIterator<Library> = delegate.getLibraryIterator()

  override fun getLibraryByName(name: String): Library? = delegate.getLibraryByName(name)

  override fun getTableLevel(): String = delegate.tableLevel

  override fun getPresentation(): LibraryTablePresentation = delegate.presentation

  override fun isEditable(): Boolean = false

  override fun getModifiableModel(): LibraryTable.ModifiableModel = delegate.modifiableModel

  override fun addListener(listener: LibraryTable.Listener) = delegate.addListener(listener)

  override fun addListener(listener: LibraryTable.Listener, parentDisposable: Disposable) = delegate.addListener(listener, parentDisposable)

  override fun removeListener(listener: LibraryTable.Listener) = delegate.removeListener(listener)

  override fun readExternal(element: Element): Unit = delegate.readExternal(element)

  override fun writeExternal(element: Element): Unit = delegate.writeExternal(element)

  internal fun getDelegate(): LibraryTable = delegate

  override fun dispose() {
    if (delegate is Disposable) Disposer.dispose(delegate)
  }
}

private class LegacyCustomLibraryTable(private val level: String, private val presentation: LibraryTablePresentation)
  : LibraryTableBase(), CustomLibraryTable {
  override fun getTableLevel(): String = level

  override fun getPresentation(): LibraryTablePresentation = presentation

  override fun readExternal(element: Element) {
    super.readExternal(element)
  }

  override fun writeExternal(element: Element) {
    super.writeExternal(element)
  }
}