// "Add non-null asserted (a.foo().single()!!) call" "true"
// WITH_STDLIB
// K2_ERROR: Argument type mismatch: actual type is 'Int?', but 'Int' was expected.

class A {
    fun foo(): List<Int?> = listOf()

    fun bar(i : Int, s: String) = Unit

    fun use() {
        val a = A()
        a.bar(a.foo().<caret>single(), "Asd")
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix