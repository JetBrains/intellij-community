// WITH_STDLIB
fun foo(x: Any) {
    inlineFn {
        when (x) {
            is <error descr="[UNRESOLVED_REFERENCE]">Abc</error> -> <error descr="[UNRESOLVED_REFERENCE]">d</error>
            else -> null
        }
    }
}

inline fun inlineFn(lambda: () -> Unit) = {}
