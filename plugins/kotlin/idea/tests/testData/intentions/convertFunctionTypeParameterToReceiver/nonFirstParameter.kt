// AFTER-WARNING: Parameter 'b' is never used
// AFTER-WARNING: Parameter 'f' is never used
// AFTER-WARNING: Parameter 'i' is never used
fun foo(f: (i: Int, b: <caret>Boolean) -> String) {
    f(1, false)
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

    foo { i, b -> "${i + 1} $b" }
}