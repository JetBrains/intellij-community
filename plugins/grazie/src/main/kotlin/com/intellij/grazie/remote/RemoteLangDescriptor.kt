package com.intellij.grazie.remote

import ai.grazie.nlp.langs.LanguageISO
import java.nio.file.Path


interface RemoteLangDescriptor {
  /**
   * Returns the file name or directory name where dictionaries are stored locally.
   * In the case of simple files (for example, .jar), it simply returns the file name.
   */
  val storageName: String

  /**
   * An object that may be used to locate a file in a local file system.
   * Implementation is free to decide what the file exactly is.
   * For example, it is a jar file in case of [LanguageToolDescriptor] and a .dic file in case of [HunspellDescriptor].
   *
   * Must return a relative path to [com.intellij.grazie.GrazieDynamic.getLangDynamicFolder]
   */
  val file: Path

  /**
   * The URL from which the file can be downloaded.
   */
  val url: String

  /**
   * The size of the file in megabytes.
   */
  val size: Int

  /**
   * The ISO code of the language.
   */
  val iso: LanguageISO

  /**
   * Used to create a storage descriptor for downloader.
   *
   * In the case of simple files (for example, .jar), it simply returns the storage name.
   */
  val storageDescriptor: String
    get() = storageName
}