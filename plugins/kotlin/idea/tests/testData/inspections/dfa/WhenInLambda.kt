// WITH_STDLIB
fun foo(x: Any) {
    inlineFn {
        when (x) {
            is<error descr="Expecting a type"> </error> -> <error descr="[UNRESOLVED_REFERENCE] Unresolved reference: d">d</error>
            else -> <warning descr="[UNUSED_EXPRESSION] The expression is unused">null</warning>
        }
    }
}

inline fun inlineFn(<warning descr="[UNUSED_PARAMETER] Parameter 'lambda' is never used">lambda</warning>: () -> Unit) = {}
