// WITH_STDLIB
// AFTER-WARNING: Parameter 'filter' is never used
import java.io.File
import java.io.FilenameFilter

fun foo(filter: FilenameFilter) {}

fun bar() {
    foo(<caret>object: FilenameFilter {
        override fun accept(file: File, name: String): Boolean {
            if (file.isDirectory) return true
            return name == "x"
        }
    })
}
