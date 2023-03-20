// PROBLEM: none
// WITH_STDLIB
// LANGUAGE_VERSION: 1.9
import java.io.File

fun foo(file: File) {
    file::getAbsolutePath<caret>
}