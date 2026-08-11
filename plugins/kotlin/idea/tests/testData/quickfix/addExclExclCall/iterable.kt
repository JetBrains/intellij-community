// "Add non-null asserted (test!!) call" "true"
// K2_ERROR: ITERATOR_ON_NULLABLE
fun foo() {
    val test : Collection<Int>? = null!!
    for (i in <caret>test) { }
}


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix