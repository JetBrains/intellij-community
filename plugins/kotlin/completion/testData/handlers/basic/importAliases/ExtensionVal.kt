import java.io.File
import kotlin.io.extension as ext

fun foo(file: File): String {
    return file.<caret>
}

// IGNORE_K2
// ELEMENT: "ext"