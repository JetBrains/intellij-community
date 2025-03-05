// "Remove redundant '?'" "true"
fun main(args : Array<String>) {
    val x : Int??<caret> = 15
}

// IGNORE_K2
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveNullableFix