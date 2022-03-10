// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.exporter

import com.intellij.openapi.options.SchemeExporter
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleScheme
import org.jetbrains.idea.eclipse.codeStyleMapping.buildEclipseCodeStyleMappingTo
import org.jetbrains.idea.eclipse.importer.EclipseXmlProfileElements.*
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import javax.xml.stream.XMLOutputFactory

class EclipseCodeStyleSchemeExporter : SchemeExporter<CodeStyleScheme>() {
  override fun getExtension(): String = "xml"

  override fun exportScheme(project: Project?, scheme: CodeStyleScheme, outputStream: OutputStream) {
    exportCodeStyleSettings(scheme, outputStream)
  }

  companion object {
    @JvmStatic
    @Throws(Exception::class)
    fun exportCodeStyleSettings(scheme: CodeStyleScheme, outputStream: OutputStream) {
      val mapping = buildEclipseCodeStyleMappingTo(scheme.codeStyleSettings).exportSettings()

      outputStream.bufferedWriter(charset = StandardCharsets.UTF_8).use {
        val writer = XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(it)
        writer.apply {
          writeStartDocument("utf-8", "1.0")
          writeCharacters("\n")
          writeStartElement(PROFILES_TAG)
          writeAttribute(VERSION_ATTR, VERSION_VALUE)
          writeCharacters("\n\t")
          writeStartElement(PROFILE_TAG)
          writeAttribute(PROFILE_KIND_ATTR, PROFILE_KIND_VALUE)
          writeAttribute(NAME_ATTR, scheme.name)
          writeAttribute(VERSION_ATTR, VERSION_VALUE)

          mapping.forEach { (id, value) ->
            writeCharacters("\n\t\t")
            writeEmptyElement(SETTING_TAG)
            writeAttribute(ID_ATTR, id)
            writeAttribute(VALUE_ATTR, value)
          }
          writeCharacters("\n\t")
          writeEndElement()
          writeCharacters("\n")
          writeEndElement()
          writeEndDocument()
        }
      }
    }
  }
}