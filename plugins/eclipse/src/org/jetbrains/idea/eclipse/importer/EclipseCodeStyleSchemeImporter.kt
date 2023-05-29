// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.importer

import com.intellij.application.options.ImportSchemeChooserDialog
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.SchemeFactory
import com.intellij.openapi.options.SchemeImportException
import com.intellij.openapi.options.SchemeImporter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.codeStyle.CodeStyleScheme
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.jetbrains.idea.eclipse.EclipseBundle
import org.jetbrains.idea.eclipse.codeStyleMapping.buildEclipseCodeStyleMappingTo
import org.jetbrains.idea.eclipse.importer.EclipseXmlProfileReader.OptionHandler
import java.io.IOException
import java.io.InputStream

class EclipseCodeStyleSchemeImporter : SchemeImporter<CodeStyleScheme>, EclipseXmlProfileElements {
  override fun getSourceExtensions(): Array<String> = arrayOf("xml")

  override fun importScheme(project: Project,
                            selectedFile: VirtualFile,
                            currentScheme: CodeStyleScheme,
                            schemeFactory: SchemeFactory<out CodeStyleScheme>): CodeStyleScheme? {
    val (selectedName, selectedScheme) = ImportSchemeChooserDialog.selectOrCreateTargetScheme(project, currentScheme, schemeFactory,
                                                                                              *selectedFile.readEclipseXmlProfileNames())
                                         ?: return null
    importCodeStyleSettings(selectedFile, selectedName, selectedScheme.codeStyleSettings)
    return selectedScheme
  }

  companion object {
    val LOG = logger<EclipseCodeStyleSchemeImporter>()

    @JvmStatic
    @Throws(SchemeImportException::class)
    fun importCodeStyleSettings(external: Map<String, String>, codeStyleSettings: CodeStyleSettings) {
      val mapping = buildEclipseCodeStyleMappingTo(codeStyleSettings)
      val importProblems = mapping.importSettings(external)
      if (importProblems.isNotEmpty()) {
        importProblems.forEach { LOG.info("Unexpected value in Eclipse XML profile: $it") }
        throw SchemeImportException(
          EclipseBundle.message("eclipse.xml.profile.import.unexpected.values", importProblems.size)
          + "\n" + importProblems.joinToString(
            separator = "\n",
            limit = 5,
            truncated = EclipseBundle.message("eclipse.xml.profile.import.unexpected.values.and.x.more", importProblems.size - 5))
          + "\n" + EclipseBundle.message("eclipse.xml.profile.import.unexpected.values.see.logs"))
      }
    }

    @JvmStatic
    @Throws(SchemeImportException::class)
    fun importCodeStyleSettings(file: VirtualFile, profileName: String?, codeStyleSettings: CodeStyleSettings) {
      val external = file.readEclipseXmlProfileOptions(profileName)
      importCodeStyleSettings(external, codeStyleSettings)
    }

    @JvmStatic
    @Throws(SchemeImportException::class)
    fun VirtualFile.readEclipseXmlProfileNames(): Array<String> {
      val names: MutableSet<String> = mutableSetOf()
      this.readEclipseXmlProfile(object : OptionHandler {
        override fun handleOption(eclipseKey: String, value: String) {}
        override fun handleName(name: String?) {
          if (name != null) {
            names.add(name)
          }
        }
      })
      return names.toTypedArray()
    }

    /**
     * @param selectedProfileName Only read settings under this profile. Reads every setting in the file if null.
     */
    @JvmStatic
    @Throws(SchemeImportException::class)
    fun VirtualFile.readEclipseXmlProfileOptions(selectedProfileName: String? = null): Map<String, String> =
      this.getInputStreamAndWrapIOException().readEclipseXmlProfileOptions(selectedProfileName)

    @JvmStatic
    @Throws(SchemeImportException::class)
    fun InputStream.readEclipseXmlProfileOptions(selectedProfileName: String? = null): Map<String, String> {
      val settings = mutableMapOf<String, String>()
      this.readEclipseXmlProfile(object : OptionHandler {
        var currentProfileName: String? = null

        override fun handleOption(eclipseKey: String, value: String) {
          if (selectedProfileName == null || currentProfileName != null && currentProfileName == selectedProfileName) {
            settings[eclipseKey] = value
          }
        }
        override fun handleName(name: String?) {
          currentProfileName = name
        }
      })
      return settings
    }

    /**
     * @throws SchemeImportException
     */
    @JvmStatic
    @Throws(SchemeImportException::class)
    fun VirtualFile.readEclipseXmlProfile(handler: OptionHandler) {
      this.getInputStreamAndWrapIOException().readEclipseXmlProfile(handler)
    }

    @JvmStatic
    @Throws(SchemeImportException::class)
    fun InputStream.readEclipseXmlProfile(handler: OptionHandler) {
      this.use { EclipseXmlProfileReader(handler).readSettings(it) }
    }

    private fun VirtualFile.getInputStreamAndWrapIOException() =
      try {
        this.inputStream
      }
      catch (e: IOException) {
        throw SchemeImportException(e)
      }
  }
}