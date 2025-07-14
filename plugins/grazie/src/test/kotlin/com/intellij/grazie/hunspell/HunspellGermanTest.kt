package com.intellij.grazie.hunspell

import ai.grazie.gec.spell.en.dict.HunspellDeutschLibraryDescriptor
import com.intellij.grazie.GrazieDynamic
import com.intellij.grazie.GraziePlugin
import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.remote.HunspellDescriptor
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.NioFiles
import com.intellij.spellchecker.SpellCheckerManager.Companion.getInstance
import com.intellij.util.io.ZipUtil
import java.nio.file.Files
import kotlin.io.path.Path


class HunspellGermanTest : GrazieTestBase() {

  override fun setUp() {
    super.setUp()

    val hunspellLib = "hunspell-de-jvm-${GraziePlugin.Hunspell.version}.jar"
    val zipPath = PathManager.getJarPathForClass(HunspellDeutschLibraryDescriptor::class.java)
    if (zipPath == null) {
      fail("$hunspellLib not found in classpath")
    }
    val zip = Path(zipPath!!)
    if (!Files.exists(zip)) {
      fail("$hunspellLib not found in classpath")
    }
    val deDir = GrazieDynamic.getLangDynamicFolder(Lang.GERMANY_GERMAN)
    val outputDir = deDir.resolve(HunspellDescriptor.GERMAN.storageName)
    Files.createDirectories(outputDir)
    ZipUtil.extract(zip, outputDir, HunspellDescriptor.filenameFilter())
    getInstance(project).loadDictionary(deDir.resolve(HunspellDescriptor.GERMAN.file).toString())
  }

  override fun tearDown() {
    try {
      getInstance(project).removeDictionary(HunspellDescriptor.GERMAN.file.toString())
      NioFiles.deleteRecursively(GrazieDynamic.getLangDynamicFolder(Lang.GERMANY_GERMAN))
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun `test hunspell de`() {
    runHighlightTestForFile("hunspell/Hunspell.java")
  }
}