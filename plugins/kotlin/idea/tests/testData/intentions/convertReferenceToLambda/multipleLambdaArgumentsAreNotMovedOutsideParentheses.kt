// AFTER-WARNING: Parameter 'f1' is never used
// AFTER-WARNING: Parameter 'f2' is never used
// AFTER-WARNING: Parameter 'i' is never used

fun test() {
    foo1({}, ::convert<caret>)
}

fun foo1(f1: () -> Unit, f2: (Int) -> Unit) {}

fun convert(i: Int) {}