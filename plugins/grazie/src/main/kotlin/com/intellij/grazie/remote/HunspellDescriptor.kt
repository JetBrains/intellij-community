package com.intellij.grazie.remote

import ai.grazie.nlp.langs.LanguageISO
import com.intellij.grazie.GraziePlugin
import java.io.FilenameFilter
import java.nio.file.Path
import kotlin.io.path.Path

private const val DE_JAR_CHECKSUM = "4fb1e06de9e4afd0b6586fe17bc681aa"
private const val RU_JAR_CHECKSUM = "0c8f5760f3ed03e3a20f754d3bfcf190"
private const val UK_JAR_CHECKSUM = "0ce677d2f1063e5d8906e4726516970d"

private const val DE_CONTENT_CHECKSUM = "4d269353dbcc8b66ce926e87c6be677d"
private const val RU_CONTENT_CHECKSUM = "b46a78bf404f692a14019098954c2c72"
private const val UK_CONTENT_CHECKSUM = "2230289ef1b8b9c321dd0ef668fb6681"

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