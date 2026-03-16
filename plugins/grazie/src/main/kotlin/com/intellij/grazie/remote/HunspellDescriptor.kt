package com.intellij.grazie.remote

import ai.grazie.nlp.langs.LanguageISO
import com.intellij.grazie.GraziePlugin
import java.io.FilenameFilter
import java.nio.file.Path
import kotlin.io.path.Path

private const val DE_CHECKSUM = "4b081d97f9c7613236cf5ad25c50acb6"
private const val RU_CHECKSUM = "b422388707c5aee27a5ec3682d5f2c22"
private const val UK_CHECKSUM = "24a0ed883d4bf786f9f89d2308d3230f"

enum class HunspellDescriptor(
  override val iso: LanguageISO,
  val isGplLicensed: Boolean,
  override val size: Int,
  override val checksum: String
) : RemoteLangDescriptor {
  RUSSIAN(LanguageISO.RU, isGplLicensed = false, 2, RU_CHECKSUM),
  GERMAN(LanguageISO.DE, isGplLicensed = true, 2, DE_CHECKSUM),
  UKRAINIAN(LanguageISO.UK, isGplLicensed = true, 2, UK_CHECKSUM);

  override val storageDescriptor: String by lazy { "$storageName.jar" }
  override val storageName: String by lazy { "hunspell-$iso-${GraziePlugin.Hunspell.version}" }
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