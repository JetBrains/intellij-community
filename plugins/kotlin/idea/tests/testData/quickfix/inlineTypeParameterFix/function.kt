// "Inline type parameter" "true"
// K2_ERROR: INITIALIZER_TYPE_MISMATCH

data class DC(val x: Int, val y: String) {
    fun <S : Int<caret>> foo() {
        val a: S = Int.MAX_VALUE
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InlineTypeParameterFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InlineTypeParameterFix