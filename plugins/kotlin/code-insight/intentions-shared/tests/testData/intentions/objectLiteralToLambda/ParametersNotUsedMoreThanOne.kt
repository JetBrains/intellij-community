// WITH_STDLIB
// AFTER-WARNING: Parameter 'file' is never used, could be renamed to _
// AFTER-WARNING: Parameter 'filter' is never used
// AFTER-WARNING: Parameter 'name' is never used, could be renamed to _
import java.io.File
import java.io.FilenameFilter

fun foo(filter: FilenameFilter) {}

fun bar() {
    foo(<caret>object: FilenameFilter {
        override fun accept(file: File, name: String) = true
    })
}
