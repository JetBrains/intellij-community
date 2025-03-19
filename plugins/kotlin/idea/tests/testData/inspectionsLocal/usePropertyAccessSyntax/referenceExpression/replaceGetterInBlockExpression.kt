// FIX: Use property access syntax
// HIGHLIGHT: GENERIC_ERROR_OR_WARNING
// WITH_STDLIB

import java.io.File

fun test(file: File) {
    with(file) {
        <caret>getParentFile()
    }
}