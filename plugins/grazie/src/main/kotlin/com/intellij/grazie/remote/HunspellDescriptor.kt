package com.intellij.grazie.remote

import ai.grazie.nlp.langs.LanguageISO
import com.intellij.grazie.GraziePlugin
import java.io.FilenameFilter
import java.nio.file.Path
import kotlin.io.path.Path

private const val DE_JAR_CHECKSUM = "5ded4554c6cd7adfdd8e914a77be37e1"
private const val DE_CONTENT_CHECKSUM = "ab08a81098d187b4d202f994b7026969"
private const val RU_JAR_CHECKSUM = "ad02bd9ecda976da279d12f048fe62e4"
private const val RU_CONTENT_CHECKSUM = "08112870a89d9a9fcd0ac0064ea302d9"
private const val UK_JAR_CHECKSUM = "cc85ac4954c2ed66d47fdfa33a85ab48"
private const val UK_CONTENT_CHECKSUM = "29ad43fe3b9835cd6e27b4a94819c4c3"

enum class HunspellDescriptor(
  override val iso: LanguageISO,
  val isGplLicensed: Boolean,
  override val size: Int,
  override val checksum: String,
  override val contentChecksum: String,
) : RemoteLangDescriptor {
  RUSSIAN(LanguageISO.RU, isGplLicensed = false, 2, RU_JAR_CHECKSUM, RU_CONTENT_CHECKSUM),
  GERMAN(LanguageISO.DE, isGplLicensed = true, 2, DE_JAR_CHECKSUM, DE_CONTENT_CHECKSUM),
  UKRAINIAN(LanguageISO.UK, isGplLicensed = true, 2, UK_JAR_CHECKSUM, UK_CONTENT_CHECKSUM);

  override val storageDescriptor: String by lazy { "hunspell-$iso-${GraziePlugin.Hunspell.version}.jar" }
  override val storageName: String by lazy { "hunspell-$iso-$contentChecksum" }
  override val file: Path by lazy { Path(storageName).resolve(DICTIONARY_DIR).resolve("$iso.dic") }
  override val url: String by lazy { "${GraziePlugin.Hunspell.url}/hunspell-$iso/${GraziePlugin.Hunspell.version}/$storageDescriptor" }

  companion object {
    private const val DICTIONARY_DIR: String = "dictionary"
    private const val RULE_DIR: String = "rule"

    /**
     * Filter that is used to unpack hunspell jar dictionary.
     * It only retains the content of the "dictionary" directory, licenses and notice files
     */
    fun filenameFilter(): FilenameFilter {
      return FilenameFilter { dir, name ->
        dir.name == HunspellDescriptor.DICTIONARY_DIR || dir.parent == HunspellDescriptor.DICTIONARY_DIR ||
        dir.name == RULE_DIR || dir.parent == RULE_DIR ||
        name.startsWith("GPL") || name.equals("license") || name.equals("notice")
      }
    }
  }
}