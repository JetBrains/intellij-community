// WITH_STDLIB
fun foo(x: Any) {
    inlineFn {
        when (x) {
            is<error descr="Expecting a type"> </error> -> <error descr="[UNRESOLVED_REFERENCE]">d</error>
            else -> <warning descr="[UNUSED_EXPRESSION]">null</warning>
        }
    }
}

inline fun inlineFn(<warning descr="[UNUSED_PARAMETER]">lambda</warning>: () -> Unit) = {}
