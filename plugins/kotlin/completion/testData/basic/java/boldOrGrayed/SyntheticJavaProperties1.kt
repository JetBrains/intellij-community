// FIR_IDENTICAL
// FIR_COMPARISON
import java.io.File

class C : File("") {
    val v: Int = 0

    override fun isFile(): Boolean {
        return true
    }
}

fun foo(c: C) {
    c.<caret>
}

// EXIST: {"lookupString":"absolutePath","tailText":" (from getAbsolutePath())","typeText":"String","attributes":"","allLookupStrings":"absolutePath, getAbsolutePath","itemText":"absolutePath","icon":"org/jetbrains/kotlin/idea/icons/field_value.svg"}
// EXIST: { lookupString: "isFile", itemText: "isFile", tailText: " (from isFile())", typeText: "Boolean", attributes: "", icon: "org/jetbrains/kotlin/idea/icons/field_value.svg"}
// EXIST: { lookupString: "v", itemText: "v", attributes: "bold", icon: "org/jetbrains/kotlin/idea/icons/field_value.svg"}
