// AFTER-WARNING: Parameter 'convert' is never used
// AFTER-WARNING: Parameter 'i' is never used

fun test() {
    foo1(convert = ::convert<caret>)
}

fun foo1(convert: (Int) -> Unit) {}

fun convert(i: Int) {}