// FIR_IDENTICAL
// FIR_COMPARISON
import java.io.File

fun foo(file: File) {
    file.g<caret>
}

// EXIST: absolutePath
// ABSENT: getAbsolutePath