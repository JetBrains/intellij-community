// !DIAGNOSTICS_NUMBER: 1
// !DIAGNOSTICS: TYPE_MISMATCH

fun foo(x: MutableCollection<out CharSequence>, y: MutableCollection<CharSequence>) {
    x.addAll(y)
}
