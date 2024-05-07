// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.inline

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.util.DocumentUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition

class InlineWatch(val expression: XExpression, var position: XSourcePosition) {
  private val myFile = position.file

  @Volatile
  private var myRangeMarker: RangeMarker? = null

  val line: Int
    get() = position.line

  fun updatePosition(): Boolean {
    val rangeMarker = myRangeMarker
    if (rangeMarker?.isValid == true) {
      val line = rangeMarker.document.getLineNumber(rangeMarker.startOffset)
      if (line != position.line) {
        position = XDebuggerUtil.getInstance().createPosition(myFile, line)!!
      }
      return true
    }
    return false
  }


  @RequiresReadLock
    /**
     * @return true if marker was added successfully
     */
  fun setMarker(): Boolean {
    if (myRangeMarker != null) return true
    // try not to decompile files
    var document = FileDocumentManager.getInstance().getCachedDocument(myFile)
    if (document == null) {
      if (myFile.fileType.isBinary()) return true
      document = FileDocumentManager.getInstance().getDocument(myFile)
    }
    if (myRangeMarker != null) return true // an extra check for myRangeMarker because we call setMarker from fileContentLoaded
    if (document != null) {
      val line = position.line
      if (DocumentUtil.isValidLine(line, document)) {
        val offset = document.getLineEndOffset(line)
        myRangeMarker = document.createRangeMarker(offset, offset, true)
        return true
      }
    }
    return false
  }
}
