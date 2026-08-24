package com.intellij.grazie.remote

import ai.grazie.nlp.langs.LanguageISO
import com.intellij.grazie.GraziePlugin
import java.io.FilenameFilter
import java.nio.file.Path
import kotlin.io.path.Path

private const val DE_JAR_CHECKSUM = "a7c2f018a3e7e62107794c5a777b9653"
private const val DE_CONTENT_CHECKSUM = "5b5735ba0df7323acfba443b527716bd"
private const val RU_JAR_CHECKSUM = "adf6181bad11d710e9384531f4263e7f"
private const val RU_CONTENT_CHECKSUM = "3f7dc0d2e21fb8c160bec0ed56e7e012"
private const val UK_JAR_CHECKSUM = "1297dd7fd0b783128fe96b127cb5c13b"
private const val UK_CONTENT_CHECKSUM = "b9acebacd83747bf840395f75ddd550c"

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