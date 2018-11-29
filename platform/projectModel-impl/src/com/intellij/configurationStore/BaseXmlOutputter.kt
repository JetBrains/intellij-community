// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import org.jdom.DocType
import java.io.IOException
import java.io.Writer

abstract class BaseXmlOutputter(protected val lineSeparator: String) {
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
      out.write('"'.toInt())
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
    if (internalSubset != null && !internalSubset.isEmpty()) {
      out.write(" [")
      out.write(lineSeparator)
      out.write(docType.internalSubset)
      out.write("]")
    }
    out.write(">")
  }
}