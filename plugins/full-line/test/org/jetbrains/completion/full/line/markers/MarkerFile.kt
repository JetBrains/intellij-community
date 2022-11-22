package org.jetbrains.completion.full.line.markers

import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.DirectoryFileFilter
import org.apache.commons.io.filefilter.RegexFileFilter
import java.io.File
import kotlin.reflect.full.memberProperties

class MarkerFile(language: String, file: File, rootFolder: File, escape: Boolean = true) {
    val testcase: String
    val code: String
    val prefix: String
    val offset: Int
    val filename: String
    val result: String

    init {
        val comment = commentLiterals(language)
        // First 2 lines of each test must contain prefix and result values
        // Rest of code is marker data
        file.readText()
            .lines()
            .apply {
                val commentSection = takeWhile { it.startsWith(comment) || it.isBlank() }
                    .map { it.removePrefix(comment) }
                result = findExtraData(commentSection, "result")
                prefix = try {
                    findExtraData(commentSection, "prefix")
                } catch (e: Exception) {
                    ""
                }

                drop(commentSection.size).joinToString("\n").apply {
                    offset = lastIndexOf("<caret>")
                    code = take(offset).let {
                        if (escape) {
                            it.replace("\"", "\\\"")
                                .replace("\n", "\\n")
                                .replace("\t", "\\t")
                        } else {
                            it
                        }
                    }
                }
            }
        file.relativeTo(rootFolder).apply {
            testcase = nameWithoutExtension
            filename = parent
        }
    }

    fun toMap(): Map<String, String> {
        val props = MarkerFile::class.memberProperties.associateBy { it.name }
        return props.keys.associateWith { props[it]?.get(this).toString() }
    }

    private fun commentLiterals(language: String): String {
        return when (language.toLowerCase()) {
            "python" -> "#"
            "java"   -> "//"
            "kotlin" -> "//"
            else     -> throw IllegalArgumentException("Passed unknown language")
        }
    }

    private fun findExtraData(comments: List<String>, prop: String): String {
        return comments.map { it.trim() }.first {
            it.matches(Regex("$prop\\s*=\\s*<.*>"))
        }.let {
            it.substring("$prop = <".length, it.length - 1)
        }
    }

    companion object {
        @JvmStatic
        fun readMarkers(rootFolder: File, language: String): List<Map<String, String>> {
            val dirs = FileUtils.listFiles(rootFolder, RegexFileFilter("^(.*?)"), DirectoryFileFilter.DIRECTORY)
            return dirs.map { MarkerFile(language, it, rootFolder).toMap() }
        }
    }
}
