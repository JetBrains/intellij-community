// WITH_STDLIB
// AFTER-WARNING: Parameter 'filter' is never used
import java.io.File
import java.io.FileFilter

fun foo(filter: FileFilter) {}

fun bar() {
    foo(<caret>object: FileFilter {
        override fun accept(file: File) = file.name.startsWith("a")
    })
}
