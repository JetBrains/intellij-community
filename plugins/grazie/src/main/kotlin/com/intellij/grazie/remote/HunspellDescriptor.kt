package com.intellij.grazie.remote

import ai.grazie.nlp.langs.LanguageISO
import com.intellij.grazie.GraziePlugin
import java.io.FilenameFilter
import java.nio.file.Path
import kotlin.io.path.Path

private const val DE_JAR_CHECKSUM = "14f7601051d9ba7e42ccaf9c4e7cf911"
private const val DE_CONTENT_CHECKSUM = "a63f05b26d352ad91ba28dc7755caae4"
private const val RU_JAR_CHECKSUM = "f744cb4af8e8180e94987976e151d41e"
private const val RU_CONTENT_CHECKSUM = "68b122b14f056a47d60f5eeefe80e231"
private const val UK_JAR_CHECKSUM = "1be2def4ab90f0c7b8452ffabf10262b"
private const val UK_CONTENT_CHECKSUM = "a7e9596f25b0141eb6109a58e51640fc"

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