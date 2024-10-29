// "Make 'inlineFun' internal" "true"
open class Base {
    fun baseFun(param: Any) {}
}

open class Derived : Base() {
    inline fun inlineFun() {
        <caret>super.baseFun("123")
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories$ChangeToInternalModCommandAction