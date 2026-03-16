// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.inline

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.util.DocumentUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition

/**
 * A watch that is shown in editor at the specified [position]
 * and evaluated only when the debugger is suspended in the same file.
 */
class InlineWatch(val expression: XExpression, position: XSourcePosition) {
  private val myFile = position.file

  @Volatile
  private var myRangeMarker: RangeMarker? = null

  var position: XSourcePosition = position
    private set

  val line: Int
    get() = position.line

  /**
   * Update the watch after a document update.
   * Check the installed [RangeMarker] validity, update position if needed.
   *
   * @return true if the watch is still valid after update, false otherwise
   */
  fun updatePosition(): Boolean {
    val rangeMarker = myRangeMarker
    if (rangeMarker == null) return true // marker is not yet created - do nothing
    if (!rangeMarker.isValid) return false // invalid marker (file was deleted) - remove the watch
    val line = rangeMarker.document.getLineNumber(rangeMarker.startOffset)
    if (line != position.line) {
      position = XDebuggerUtil.getInstance().createPosition(myFile, line)!!
    }
    return true
  }

  /**
   * Install [RangeMarker] for this watch.
   * @return true if a marker was added successfully
   */
  fun setMarker(): Boolean {
    ThreadingAssertions.assertReadAccess()
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
