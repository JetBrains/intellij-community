// "Remove redundant '?'" "true"
fun main(args : Array<String>) {
    val x : Int??<caret> = 15
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveNullableFix
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveNullableFix