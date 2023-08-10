// "Add non-null asserted (!!) call" "true"
class C {
    val s: String? = null
}

// Test for KTIJ-10052
fun C.test() {
    var z: String = ""
    z = <caret>s
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix