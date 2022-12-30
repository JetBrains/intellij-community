// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import org.jdom.DocType
import org.jdom.ProcessingInstruction
import java.io.IOException
import java.io.Writer

abstract class BaseXmlOutputter(protected val lineSeparator: String) {
  companion object {
    fun doesNameSuggestSensitiveInformation(name: String): Boolean {
      if (name.contains("password")) {
        val isRemember = name.contains("remember", ignoreCase = true) ||
                         name.contains("keep", ignoreCase = true) ||
                         name.contains("use", ignoreCase = true) ||
                         name.contains("save", ignoreCase = true) ||
                         name.contains("stored", ignoreCase = true)
        return !isRemember
      }
      return false
    }
  }

  /**
   * This handle printing the DOCTYPE declaration if one exists.
   *
   * @param docType `Document` whose declaration to write.
   * @param out     `Writer` to use.
   */
  @Throws(IOException::class)
  protected fun printDocType(out: Writer, docType: DocType) {
    val publicID = docType.publicID
    val systemID = docType.systemID
    val internalSubset = docType.internalSubset
    var hasPublic = false

    out.write("<!DOCTYPE ")
    out.write(docType.elementName)
    if (publicID != null) {
      out.write(" PUBLIC \"")
      out.write(publicID)
      out.write('"'.code)
      hasPublic = true
    }
    if (systemID != null) {
      if (!hasPublic) {
        out.write(" SYSTEM")
      }
      out.write(" \"")
      out.write(systemID)
      out.write("\"")
    }
    if (!internalSubset.isNullOrEmpty()) {
      out.write(" [")
      out.write(lineSeparator)
      out.write(docType.internalSubset)
      out.write("]")
    }
    out.write(">")
  }

  @Throws(IOException::class)
  protected fun writeProcessingInstruction(out: Writer, pi: ProcessingInstruction, target: String) {
    out.write("<?")
    out.write(target)

    val rawData = pi.data
    if (!rawData.isNullOrEmpty()) {
      out.write(" ")
      out.write(rawData)
    }

    out.write("?>")
  }
}