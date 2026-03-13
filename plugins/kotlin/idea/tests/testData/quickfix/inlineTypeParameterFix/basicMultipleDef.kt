// "Inline type parameter" "true"
// K2_ERROR: Initializer type mismatch: expected 'T (of class DC<T : Int, S : String>)', actual 'Int'.
// K2_ERROR: Initializer type mismatch: expected 'T (of class DC<T : Int, S : String>)', actual 'Int'.

data class DC<T : Int<caret>, S : String>(val x: T, val y: String) {
    var a: T = Int.MAX_VALUE

    fun foo(b: T) {
        val c: T = Int.MIN_VALUE
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InlineTypeParameterFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InlineTypeParameterFix