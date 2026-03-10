// "Add non-null asserted (s!!) call" "true"
// K2_ERROR: Initializer type mismatch: expected 'String', actual 'String?'.
class C {
    val s: String? = null
}

// Test for KTIJ-10052
fun C.test() {
    var z: String = <caret>s
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix