package com.intellij.findUsagesMl

import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.ml.api.feature.*
import org.apache.commons.text.similarity.JaroWinklerSimilarity


data class FindUsagesRankingFileInfo(
  val queryNames: List<String>,
  val queryFiles: List<VirtualFile>,
  val candidateFile: VirtualFile?,
  val recentFilesList: List<VirtualFile> = listOf(),
  val timeStamp: Long,
)

object FindUsagesFileRankerFeatures {
  val QUERY_JARO_WINKLER_SIMILARITY: FeatureDeclaration<Double> = FeatureDeclaration.double(name = "query_jaro_winkler_similarity") { "Jaro-Winkler similarity of syntactic element to file name" }
  val FILENAME_JARO_WINKLER_SIMILARITY: FeatureDeclaration<Double> = FeatureDeclaration.double(name = "filename_jaro_winkler_similarity") { "Jaro-Winkler similarity of the files' names" }
  val QUERY_FILE_TYPE: FeatureDeclaration<String> = FeatureDeclaration.string(name = "query_file_type", "file_type") { "Query file's type" }
  val CANDIDATE_TYPE: FeatureDeclaration<String> = FeatureDeclaration.string(name = "file_type", "file_type") { "Candidate file's type" }
  val CANDIDATE_LENGTH: FeatureDeclaration<Long?> = FeatureDeclaration.long(name = "file_length") { "Candidate file's length" }.nullable()
  val FILE_TYPE_SAME: FeatureDeclaration<Boolean> = FeatureDeclaration.boolean(name = "file_type_same") { "True if the query and the candidate have the same file type" }
  val QUERY_COUNT: FeatureDeclaration<Int> = FeatureDeclaration.int(name = "query_count") { "The number of differing query texts" }
  val TIME_MODIFIED_DIFFERENCE_MS: FeatureDeclaration<Long> = FeatureDeclaration.long(name = "time_modified_difference_ms") { "Query file's modified timestamp - candidate file's modified timestamp in ms" }
  val TIME_SINCE_LAST_MODIFIED_MS: FeatureDeclaration<Long> = FeatureDeclaration.long(name = "time_since_modified_ms") { "Time since candidate file's modified timestamp in ms at the time of feature calculation" }
  val RECENT_FILES_INDEX: FeatureDeclaration<Int> = FeatureDeclaration.int(name = "recent_files_index") { "Index of the candidate file in the list of the most recent files" }
  val DIRECTORY_DISTANCE: FeatureDeclaration<Double> = FeatureDeclaration.double(name = "directory_distance") { "Normalized distance between the query file and candidate file" }

  fun declarations(): List<List<FeatureDeclaration<*>>> = listOf(extractFeatureDeclarations(FindUsagesFileRankerFeatures))
}

class FindUsagesFileRankerFeatureProvider : FeatureProvider<FindUsagesRankingFileInfo>() {
  override val featureDeclarations: List<FeatureDeclaration<*>> = extractFeatureDeclarations(FindUsagesFileRankerFeatures)

  override fun computeFeatures(instance: FindUsagesRankingFileInfo, requiredOutput: FeatureSet): List<Feature> = buildLazyFeaturesList(requiredOutput) {
    if (instance.candidateFile != null) {
      add(FindUsagesFileRankerFeatures.QUERY_JARO_WINKLER_SIMILARITY) {
        instance.queryNames.maxOfOrNull {
          JaroWinklerSimilarity().apply(it, instance.candidateFile.nameWithoutExtension)
        } ?: 0.0
      }
      add(FindUsagesFileRankerFeatures.FILENAME_JARO_WINKLER_SIMILARITY) {
        instance.queryFiles.maxOfOrNull {
          JaroWinklerSimilarity().apply(it.nameWithoutExtension, instance.candidateFile.nameWithoutExtension)
        } ?: 0.0
      }
      add(FindUsagesFileRankerFeatures.QUERY_FILE_TYPE) { instance.queryFiles.iterator().next().fileType.name }
      add(FindUsagesFileRankerFeatures.CANDIDATE_TYPE) { instance.candidateFile.fileType.name }
      add(FindUsagesFileRankerFeatures.CANDIDATE_LENGTH) { instance.candidateFile.length }
      add(FindUsagesFileRankerFeatures.FILE_TYPE_SAME) { instance.queryFiles.map { it.fileType == instance.candidateFile.fileType }.any() }
      add(FindUsagesFileRankerFeatures.QUERY_COUNT) { instance.queryNames.size }
      add(FindUsagesFileRankerFeatures.TIME_MODIFIED_DIFFERENCE_MS) { instance.queryFiles.minOf { it.timeStamp - instance.candidateFile.timeStamp } }
      add(FindUsagesFileRankerFeatures.TIME_SINCE_LAST_MODIFIED_MS) { instance.timeStamp - instance.candidateFile.timeStamp }
      add(FindUsagesFileRankerFeatures.RECENT_FILES_INDEX) { getRecentFilesIndex(instance.recentFilesList, instance.candidateFile) }
      add(FindUsagesFileRankerFeatures.DIRECTORY_DISTANCE) { calculateDirectoryDistance(instance.queryFiles, instance.candidateFile) }
    }
  }

  private fun calculateDirectoryDistance(queryFiles: List<VirtualFile>, candidateFile: VirtualFile): Double {
    // use the minimum of query files
    return queryFiles.minOfOrNull { queryFile -> computeNormalizedDistance(queryFile, candidateFile) } ?: 1.0
  }

  /**
   * Compute the normalized distance between the query file and the candidate file.
   * Including the files itself.
   *
   * The distance is normalized to the range [0, 1], where 0 means the files are the same,
   * and 1 means they are in completely different directories.
   */
  private fun computeNormalizedDistance(queryFile: VirtualFile, candidateFile: VirtualFile): Double {
    val queryPath = queryFile.path
    val candidatePath = candidateFile.path

    // split path to components, VirtualFile uses '/' internally
    val queryPathComponents = queryPath.split('/')
    val candidatePathComponents = candidatePath.split('/')

    // common prefix length
    val commonPrefixLength = queryPathComponents.zip(candidatePathComponents)
      .takeWhile { (a, b) -> a == b }
      .count()

    val totalLength = queryPathComponents.size + candidatePathComponents.size - 2 * commonPrefixLength

    // normalize the distance, 0 is the same directory, 1 is completely different
    return if (totalLength == 0) 0.0 else 1.0 - (2.0 * commonPrefixLength) / (queryPathComponents.size + candidatePathComponents.size)
  }

  private fun getRecentFilesIndex(recentFilesList: List<VirtualFile>, candidateFile: VirtualFile): Int {
    val fileIndex = recentFilesList.indexOf(candidateFile)
    if (fileIndex == -1) {
      return fileIndex
    }

    // Give the most recent files the lowest index value
    return recentFilesList.size - fileIndex
  }
}

