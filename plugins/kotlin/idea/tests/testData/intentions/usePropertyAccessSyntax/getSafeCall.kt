// WITH_STDLIB
import java.io.File

fun foo(file: File?) {
    file?.getAbsolutePath()<caret>
}