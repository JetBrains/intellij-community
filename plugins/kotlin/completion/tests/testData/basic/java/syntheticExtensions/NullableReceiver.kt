// FIR_IDENTICAL
// FIR_COMPARISON
import java.io.File

fun foo(file: File?) {
    file.<caret>
}

// EXIST: {"lookupString":"absolutePath","tailText":" (from getAbsolutePath())","typeText":"String","attributes":"grayed","allLookupStrings":"absolutePath, getAbsolutePath","itemText":"absolutePath"}
// ABSENT: getAbsolutePath
