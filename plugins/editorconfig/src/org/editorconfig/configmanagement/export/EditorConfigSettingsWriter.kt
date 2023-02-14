// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.export

import com.intellij.application.options.codeStyle.properties.*
import com.intellij.lang.Language
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.util.containers.MultiMap
import org.editorconfig.Utils
import org.editorconfig.configmanagement.ConfigEncodingManager
import org.editorconfig.configmanagement.LineEndingsManager
import org.editorconfig.configmanagement.StandardEditorConfigProperties
import org.editorconfig.configmanagement.extended.EditorConfigIntellijNameUtil
import org.editorconfig.configmanagement.extended.EditorConfigPropertyKind
import org.editorconfig.configmanagement.extended.EditorConfigValueUtil
import org.editorconfig.configmanagement.extended.IntellijPropertyKindMap
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.*

class EditorConfigSettingsWriter(private val myProject: Project?,
                                 out: OutputStream,
                                 private val mySettings: CodeStyleSettings,
                                 private val myAddRootFlag: Boolean,
                                 private val myCommentOutProperties: Boolean) : OutputStreamWriter(out, StandardCharsets.UTF_8) {
  private var myNoHeaders = false

  // region Filters
  private var myLanguages: Set<Language> = emptySet()
  private var myPropertyKinds: Set<EditorConfigPropertyKind> = EnumSet.allOf(EditorConfigPropertyKind::class.java)
  // endregion

  private val myGeneralOptions: Map<String, String> = hashMapOf<String, String>().apply { fillGeneralOptions(this) }

  private fun fillGeneralOptions(target: MutableMap<String, String>) {
    getKeyValuePairs(GeneralCodeStylePropertyMapper(mySettings)).forEach { target[it.key] = it.value }
    target["ij_continuation_indent_size"] = mySettings.OTHER_INDENT_OPTIONS.CONTINUATION_INDENT_SIZE.toString()
    if (myProject != null) {
      val encoding = Utils.getEncoding(myProject)
      if (encoding != null) {
        target[ConfigEncodingManager.charsetKey] = encoding
      }
    }
    val lineSeparator = Utils.getLineSeparatorString(mySettings.lineSeparator)
    if (lineSeparator != null) {
      target[LineEndingsManager.lineEndingsKey] = lineSeparator
    }
    target[StandardEditorConfigProperties.INSERT_FINAL_NEWLINE] = EditorSettingsExternalizable.getInstance().isEnsureNewLineAtEOF.toString()
    val trimSpaces = Utils.getTrimTrailingSpaces()
    if (trimSpaces != null) {
      target[StandardEditorConfigProperties.TRIM_TRAILING_WHITESPACE] = trimSpaces.toString()
    }
  }

  private fun <T> createEmptyMutableSet() = hashSetOf<T>()

  fun forLanguages(languages: List<Language>): EditorConfigSettingsWriter {
    myLanguages = createEmptyMutableSet<Language>().apply { addAll(languages) }
    return this
  }

  fun forLanguages(vararg languages: Language): EditorConfigSettingsWriter {
    myLanguages = createEmptyMutableSet<Language>().apply { addAll(languages) }
    return this
  }

  fun forPropertyKinds(vararg kinds: EditorConfigPropertyKind): EditorConfigSettingsWriter {
    myPropertyKinds = EnumSet.noneOf(EditorConfigPropertyKind::class.java).apply { addAll(kinds) }
    return this
  }

  fun withoutHeaders(): EditorConfigSettingsWriter {
    myNoHeaders = true
    return this
  }

  @Throws(IOException::class)
  fun writeSettings() {
    if (myAddRootFlag) {
      writeProperties(listOf(KeyValuePair("root", "true")), false)
      write("\n")
    }
    writeGeneralSection()
    val mappers = MultiMap<String, LanguageCodeStylePropertyMapper>()
    CodeStylePropertiesUtil.collectMappers(mySettings) { mapper: AbstractCodeStylePropertyMapper? ->
      if (mapper is LanguageCodeStylePropertyMapper) {
        val fileType = mapper.language.associatedFileType
        if (fileType != null) {
          val pattern = Utils.buildPattern(fileType)
          mappers.putValue(pattern, mapper)
        }
      }
    }
    for (pattern in mappers.keySet().sorted()) {
      if (pattern.isEmpty()) continue
      var currPattern = pattern
      mappers[pattern]
        .sortedBy { it.languageDomainId }
        .forEach { mapper ->
          if (writeLangSection(mapper, currPattern)) {
            currPattern = null // Do not write again
          }
        }
    }
  }

  @Throws(IOException::class)
  private fun writeGeneralSection() {
    if (!myNoHeaders) {
      write("[*]\n")
    }
    val pairs = myGeneralOptions
      .filter { (k, _) -> isNameAllowed(k) }
      .map { (k, v) -> KeyValuePair(k, v) }
      .sortedWith(PAIR_COMPARATOR)
    writeProperties(pairs, myCommentOutProperties)
  }

  @Throws(IOException::class)
  private fun writeLangSection(mapper: LanguageCodeStylePropertyMapper, pattern: String?): Boolean {
    val language = mapper.language
    if (language in myLanguages) {
      val optionValueList = getKeyValuePairs(mapper)
      if (!optionValueList.isEmpty()) {
        if (pattern != null && !myNoHeaders) {
          write("\n[$pattern]\n")
        }
        writeProperties(optionValueList.sortedWith(PAIR_COMPARATOR), myCommentOutProperties)
        return true
      }
    }
    return false
  }

  private data class KeyValuePair(val key: String, val value: String)

  private fun getKeyValuePairs(mapper: AbstractCodeStylePropertyMapper): List<KeyValuePair> =
    buildList {
      for (property in mapper.enumProperties()) {
        val accessor = mapper.getAccessor(property)
        val name = getEditorConfigName(mapper, property)
        if (name != null && isNameAllowed(name)) {
          val value = getEditorConfigValue(accessor)
          if (isValueAllowed(value) && !(mapper is LanguageCodeStylePropertyMapper && matchesGeneral(name, value!!))) {
            add(KeyValuePair(name, value!!))
          }
        }
      }
    }

  private fun matchesGeneral(name: String, value: String): Boolean =
    myGeneralOptions[name] == value

  private fun isNameAllowed(ecName: String): Boolean =
    getPropertyKind(ecName) in myPropertyKinds

  @Throws(IOException::class)
  private fun writeProperties(pairs: List<KeyValuePair>, commentOut: Boolean) {
    for (pair in pairs) {
      if (commentOut) {
        write("# ")
      }
      write("${pair.key} = ${pair.value}\n")
    }
  }

  companion object {
    private val PAIR_COMPARATOR = Comparator { a: KeyValuePair, b: KeyValuePair ->
      val aPropertyKind = getPropertyKind(a.key)
      val bPropertyKind = getPropertyKind(b.key)
      if (aPropertyKind != bPropertyKind)
        Comparing.compare(bPropertyKind, aPropertyKind) // in reversed order
      else
        Comparing.compare(a.key, b.key)
    }

    private fun getEditorConfigValue(accessor: CodeStylePropertyAccessor<*>): String? {
      val value = accessor.asString
      return if (value.isNullOrEmpty() && CodeStylePropertiesUtil.isAccessorAllowingEmptyList(accessor))
        EditorConfigValueUtil.EMPTY_LIST_VALUE
      else
        value
    }

    private fun getPropertyKind(ecName: String): EditorConfigPropertyKind {
      val ijName = EditorConfigIntellijNameUtil.toIntellijName(ecName)
      return IntellijPropertyKindMap.getPropertyKind(ijName)
    }

    private fun isValueAllowed(value: String?): Boolean =
      value != null && !value.trim { it <= ' ' }.isEmpty()

    private fun getEditorConfigName(mapper: AbstractCodeStylePropertyMapper, propertyName: String): String? {
      val editorConfigNames = EditorConfigIntellijNameUtil.toEditorConfigNames(mapper, propertyName)
      return if (editorConfigNames.isEmpty()) {
        null
      }
      else if (editorConfigNames.size == 1) {
        editorConfigNames[0]
      }
      else {
        EditorConfigIntellijNameUtil.getLanguageProperty(mapper, propertyName)
      }
    }
  }
}