// WITH_STDLIB
// FIX: Use property access syntax
import java.io.File

fun foo(file: File?) {
    file?.getAbsolutePath<caret>()
}