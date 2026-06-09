// AFTER-WARNING: Parameter 'arg' is never used
class Bar {
    operator fun timesAssign(arg: Bar) {}
}

fun foo(b: Bar) {
    var a = Bar()
    a <caret>*= b
}
