// AFTER-WARNING: Parameter 'b' is never used
// AFTER-WARNING: Parameter 'f' is never used
// AFTER-WARNING: Parameter 'i' is never used
fun foo(f: <caret>Int.(Boolean) -> String) {
    1.f(false)
    bar(f)
}

fun bar(f: (Int, Boolean) -> String) {

}

fun lambda(): (Int, Boolean) -> String = { i, b -> "$i $b"}

fun baz(f: (Int, Boolean) -> String) {
    fun g(i: Int, b: Boolean) = ""

    foo(f)

    foo(::g)

    foo(lambda())

    foo { b -> "${this + 1} $b" }
}