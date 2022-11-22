package org.jetbrains.completion.full.line.markers

import com.intellij.lang.Language
import com.intellij.util.io.ZipUtil
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.DirectoryFileFilter
import org.apache.commons.io.filefilter.RegexFileFilter
import org.jetbrains.completion.full.line.local.LocalFullLineCompletionTestCase
import org.jetbrains.completion.full.line.platform.FullLineLookupElement
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.provider.Arguments
import java.io.File
import java.net.URL
import java.nio.file.Files

abstract class MarkerTestCase(private val language: Language) : LocalFullLineCompletionTestCase(language) {
  override fun getTestDataPath() = MARKERS_PATH

  fun test_init_model() {
    initModel()
  }

  protected fun doTestMarkers(marker: Marker) {
    val context = marker.code.take(marker.offset) + "<caret>" + marker.code.drop(marker.offset)
    //val relFilePath = if (language.id == JavaLanguage.INSTANCE.id) "../${marker.filename}" else marker.filename
    val relFilePath = marker.filename

    myFixture.addFileToProject(relFilePath, context)
    myFixture.configureByFile(relFilePath)
    myFixture.completeBasic()

    assertNotNull(myFixture.lookupElements, "Lookup is not shown")

    val variants = myFixture.lookupElements!!.filterIsInstance<FullLineLookupElement>().map { it.lookupString }

    assertTrue(variants.isNotEmpty(), "Variants are empty")
    assertTrue(variants.any { variant -> marker.result.matches(variant) },
               "One of suggestions: `${variants.joinToString("\n\t=>", prefix = "\n\t=>")}` Must match the result: `${marker.result}`.")
  }

  @Suppress("unused")
  companion object {
    private const val MARKERS_PATH = "testData/markers"
    private const val repo = "CCRM/fl-markers"
    private const val branch = "master"

    private val token = System.getenv("FLCC_MARKERS_TOKEN")

    private val root: File = Files.createTempDirectory("flcc-markers").toFile()

    fun markers(language: String): List<Arguments> {
      val rootFolder = downloadMarkers(language, root)

      if (!rootFolder.exists()) return emptyList()

      return FileUtils.listFiles(rootFolder, RegexFileFilter("^(.*?)"), DirectoryFileFilter.DIRECTORY)
        .mapIndexed { i, file -> Arguments.of(Marker.fromMap(language, file, rootFolder, false)) }
    }

    private fun getResource(name: String): File? {
      return MarkerTestCase::class.java.classLoader.getResource(name)?.path?.let { File(it) }
    }

    private fun downloadMarkers(language: String, root: File): File {
      assertNotNull(token)
      if (root.exists()) root.deleteRecursively()
      root.mkdir()

      val archive = root.resolve("archive.txt")
      val markersRoot = root.resolve("markers-data")

      URL("https://jetbrains.team/vcs/archive/$repo/refs/heads/$branch").openConnection().apply {
        setRequestProperty("Authorization", "Bearer $token")
      }.getInputStream().use { Files.copy(it, archive.toPath()) }
      ZipUtil.extract(archive.toPath(), markersRoot.toPath(), null, true)

      archive.delete()

      return markersRoot.resolve(language)
    }
  }
}
