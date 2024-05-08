// WITH_STDLIB
fun foo(x: Any) {
    inlineFn {
        when (x) {
            is <error descr="[UNRESOLVED_REFERENCE] Unresolved reference 'Abc'.">Abc</error> -> <error descr="[UNRESOLVED_REFERENCE] Unresolved reference 'd'.">d</error>
            else -> null
        }
    }
}

inline fun inlineFn(lambda: () -> Unit) = {}
