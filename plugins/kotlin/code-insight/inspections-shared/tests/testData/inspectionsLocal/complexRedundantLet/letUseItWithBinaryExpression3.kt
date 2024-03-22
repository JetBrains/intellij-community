// WITH_STDLIB
// PROBLEM: none

class File

fun nullableFile(): File? = null

fun test() {
    nullableFile()
        .let<caret> { it ?: error("") }
}