package org.jetbrains.completion.full.line.markers

import java.io.File

data class Marker(
    val testcase: String,
    val code: String,
    val offset: Int,
    val filename: String,

    val expected: List<String> = emptyList(),
    val notExpected: List<String> = emptyList(),

    val prefix: String = "",
    val result: Regex,
) {

    override fun toString() = testcase

    companion object {
        fun fromMap(language: String, file: File, rootFolder: File, escape: Boolean = true): Marker {
            return fromMap(MarkerFile(language, file, rootFolder, escape).toMap())
        }

        fun fromFile(map: Map<String, String>, mapMultiple: Map<String, List<String>>) = fromMap(map).copy(
            expected = listOfNotNull(
                mapMultiple["expected"],
                mapMultiple["example"],
                mapMultiple["sample"],
            ).flatten(),
            notExpected = listOfNotNull(
                mapMultiple["!expected"],
                mapMultiple["!example"],
                mapMultiple["!sample"],
            ).flatten()
        )

        fun fromMap(map: Map<String, String>) = object {
            val testcase by map
            val code by map
            val offset by map
            val filename by map

            val result by map
            val prefix by map

            val data = Marker(
                testcase,
                code.replaceWhitespacesWithTab(),
                offset.toInt(),
                filename,
                expected = listOf(),
                notExpected = listOf(),
                prefix,
                result = Regex(
                    result
                        .replace("{", "\\{")
//                        .replace("}", "\\}")
//                        .replace("$", "\\$")
//                        .replace("\\\\(", "\\(")
//                        .replace("\\\\)", "\\)")
                )
            )
        }.data
    }
}

const val TABULATION = "    "

private fun String.replaceWhitespacesWithTab(): String {
    val char = TABULATION.first()
    var occurrences = 0
    for (ch in this) {
        if (ch == char) {
            occurrences++
        } else {
            break
        }
    }
    return "\t".repeat(occurrences / TABULATION.length) + this.drop(occurrences)
}
