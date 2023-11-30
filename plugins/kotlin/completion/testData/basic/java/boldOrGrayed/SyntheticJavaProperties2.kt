// FIR_COMPARISON
import java.io.File

fun foo(file: File) {
    file.<caret>
}

// EXIST: {"lookupString":"absolutePath","tailText":" (from getAbsolutePath())","typeText":"String","attributes":"bold","allLookupStrings":"absolutePath, getAbsolutePath","itemText":"absolutePath","icon":"org/jetbrains/kotlin/idea/icons/field_value.svg"}
// EXIST: { lookupString: "isFile", itemText: "isFile", tailText: " (from isFile())", typeText: "Boolean", attributes: "bold", icon: "org/jetbrains/kotlin/idea/icons/field_value.svg"}
