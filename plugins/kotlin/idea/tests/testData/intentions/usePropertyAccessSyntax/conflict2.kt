// WITH_STDLIB
// IS_APPLICABLE: true
import java.io.File

val File.absolutePath: String get() = ""

fun foo(file: File) {
    file.getAbsolutePath()<caret>
}