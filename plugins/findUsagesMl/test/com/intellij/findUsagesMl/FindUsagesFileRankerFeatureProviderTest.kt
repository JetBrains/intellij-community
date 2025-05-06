package com.intellij.findUsagesMl

import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.ml.api.feature.Feature
import com.jetbrains.ml.api.feature.FeatureDeclaration
import com.jetbrains.ml.api.feature.FeatureSet
import org.apache.commons.text.similarity.JaroWinklerSimilarity
import org.junit.jupiter.api.Test
import java.lang.reflect.Method
import kotlin.test.assertEquals

/**
 * Tests for [FindUsagesFileRankerFeatureProvider]
 */
class FindUsagesFileRankerFeatureProviderTest {

  /**
   * Simple implementation of VirtualFile for testing
   */
  private class TestVirtualFile(
    private val name: String,
    private val timestamp: Long = 0,
    private val directoryPath: String = "",
  ) : VirtualFile() {
    override fun getName(): String = name
    override fun getFileSystem() = throw UnsupportedOperationException()
    override fun getPath() = directoryPath + name
    override fun isWritable() = true
    override fun isDirectory() = false
    override fun isValid() = true
    override fun getParent() = null
    override fun getChildren() = emptyArray<VirtualFile>()
    override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long) = throw UnsupportedOperationException()
    override fun contentsToByteArray() = ByteArray(0)
    override fun getTimeStamp() = timestamp
    override fun getLength() = 100L
    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {}
    override fun getInputStream() = throw UnsupportedOperationException()
    override fun getFileType() = PlainTextFileType.INSTANCE
    override fun getNameWithoutExtension(): String = name.substringBeforeLast(".")
  }

  private val candidateFile = TestVirtualFile("CandidateSearchFile.txt", 40)
  private val queryFileFurther = TestVirtualFile("QueryFile.txt", 50) // similarity is further from the candidate
  private val queryFileCloser = TestVirtualFile("QueryCandidateFile.txt", 60) // similarity is closer to the candidate
  private val searchTerm = "search_term"

  /**
   * Use reflection to access the protected computeFeatures method
   */
  private fun computeFeatures(instance: FindUsagesRankingFileInfo): List<Feature> {
    val provider = FindUsagesFileRankerFeatureProvider()
    val method: Method = FindUsagesFileRankerFeatureProvider::class.java.getDeclaredMethod(
      "computeFeatures",
      FindUsagesRankingFileInfo::class.java,
      FeatureSet::class.java
    )
    method.isAccessible = true
    return method.invoke(provider, instance, FeatureSet.ALL) as List<Feature>
  }

  @Test
  fun testFilenameJaroSimilarity() {
    val expectedJaroSimilarity = JaroWinklerSimilarity().apply(queryFileCloser.nameWithoutExtension, candidateFile.nameWithoutExtension)
    val featureValue = findFeature(computeFeatures(makeFileInfo()), FindUsagesFileRankerFeatures.FILENAME_JARO_WINKLER_SIMILARITY)!!.asDoubleFeature.doubleValue

    assertEquals(expectedJaroSimilarity, featureValue, "FILENAME_JARO_SIMILARITY value should match expected")
    assertEquals(0.741, featureValue, 0.01, "FILENAME_JARO_SIMILARITY value should match expected")
  }

  @Test
  fun testQueryJaroSimilarity() {
    val jaroSimilarity = JaroWinklerSimilarity().apply(searchTerm, candidateFile.nameWithoutExtension)
    val featureValue = findFeature(computeFeatures(makeFileInfo()), FindUsagesFileRankerFeatures.QUERY_JARO_WINKLER_SIMILARITY)!!.asDoubleFeature.doubleValue

    assertEquals(jaroSimilarity, featureValue, "QUERY_JARO_SIMILARITY value should match expected")
    assertEquals(0.473, featureValue, 0.01, "QUERY_JARO_SIMILARITY value should match expected")
  }

  @Test
  fun testTimeDifference() {
    val featureValue = findFeature(computeFeatures(makeFileInfo()), FindUsagesFileRankerFeatures.TIME_MODIFIED_DIFFERENCE_MS)!!.asInt64Feature.int64Value
    assertEquals(10, featureValue, "TIME_MODIFIED_DIFFERENCE_MS should match")
  }

  @Test
  fun testRecentFilesIndex() {
    val fileInfo = makeFileInfo()

    val featureValueIsRecent = findFeature(computeFeatures(fileInfo), FindUsagesFileRankerFeatures.RECENT_FILES_INDEX)!!.asInt32Feature.int32Value
    assertEquals(1, featureValueIsRecent, "RECENT_FILES_INDEX 1")

    val fileInfo2 = makeFileInfo(TestVirtualFile("FileNotInRecent.txt")) // input some new file not in the recent files index

    val featureValueNotRecent = findFeature(computeFeatures(fileInfo2), FindUsagesFileRankerFeatures.RECENT_FILES_INDEX)!!.asInt32Feature.int32Value
    assertEquals(-1, featureValueNotRecent, "RECENT_FILES_INDEX not in list")
  }

  @Test
  fun testDirectoryDistance() {
    // Create test files with specific directory structures
    val queryFile1 = TestVirtualFile("file1.txt", directoryPath = "/dir1/subdir1/")
    val queryFile2 = TestVirtualFile("file2.txt", directoryPath = "/dir1/subdir2/")

    val candidateFile1 = TestVirtualFile("candidateFile1.txt", directoryPath = "/dir1/subdir1/")
    val candidateFile2 = TestVirtualFile("candidateFile2.txt", directoryPath = "/dir1/subdir2/")
    val candidateFile3 = TestVirtualFile("candidateFile3.txt", directoryPath = "/dir3/subdir3/")

    // Test case 1: Same directory - distance should be 0
    val fileInfo1 = FindUsagesRankingFileInfo(
      queryNames = listOf("query"),
      queryFiles = listOf(queryFile1),
      candidateFile = candidateFile1,
      timeStamp = System.currentTimeMillis()
    )
    val distanceValue1 = findFeature(computeFeatures(fileInfo1), FindUsagesFileRankerFeatures.DIRECTORY_DISTANCE)!!.asDoubleFeature.doubleValue
    assertEquals(0.25, distanceValue1, 0.001, "DIRECTORY_DISTANCE should be 0 for files in the same directory")

    // Test case 2: Different subdirectories but same parent - distance should be moderate
    val fileInfo2 = FindUsagesRankingFileInfo(
      queryNames = listOf("query"),
      queryFiles = listOf(queryFile1),
      candidateFile = candidateFile2,
      timeStamp = System.currentTimeMillis()
    )
    val distanceValue2 = findFeature(computeFeatures(fileInfo2), FindUsagesFileRankerFeatures.DIRECTORY_DISTANCE)!!.asDoubleFeature.doubleValue
    assertEquals(0.5, distanceValue2, 0.001, "DIRECTORY_DISTANCE should be moderate for files in different subdirectories")

    // Test case 3: Completely different directories - distance should be higher
    val fileInfo3 = FindUsagesRankingFileInfo(
      queryNames = listOf("query"),
      queryFiles = listOf(queryFile1),
      candidateFile = candidateFile3,
      timeStamp = System.currentTimeMillis()
    )
    val distanceValue3 = findFeature(computeFeatures(fileInfo3), FindUsagesFileRankerFeatures.DIRECTORY_DISTANCE)!!.asDoubleFeature.doubleValue
    assertEquals(0.75, distanceValue3, 0.001, "DIRECTORY_DISTANCE should be higher for files in completely different directories")

    // Test case 4: Multiple query files - should take the minimum distance
    val fileInfo4 = FindUsagesRankingFileInfo(
      queryNames = listOf("query"),
      queryFiles = listOf(queryFile1, queryFile2),
      candidateFile = candidateFile2,
      timeStamp = System.currentTimeMillis()
    )

    val distanceValue4 = findFeature(computeFeatures(fileInfo4), FindUsagesFileRankerFeatures.DIRECTORY_DISTANCE)!!.asDoubleFeature.doubleValue
    assertEquals(0.25,distanceValue4, 0.001, "DIRECTORY_DISTANCE should take the minimum distance from multiple query files")
  }

  private fun findFeature(features: List<Feature>, declaration: FeatureDeclaration<*>): Feature? {
    return features.find { it.name == declaration.name }
  }

  private fun makeFileInfo(inputCandidateFile: VirtualFile = candidateFile): FindUsagesRankingFileInfo {
    return FindUsagesRankingFileInfo(
      queryNames = listOf(searchTerm),
      queryFiles = listOf(queryFileFurther, queryFileCloser),
      candidateFile = inputCandidateFile,
      recentFilesList = listOf(TestVirtualFile("RecentFile.txt"), candidateFile), // this.candidateFile used specifically for the testRecentFilesIndex test
      timeStamp = System.currentTimeMillis()
    )
  }
}