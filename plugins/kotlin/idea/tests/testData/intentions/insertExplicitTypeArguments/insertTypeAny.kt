// IS_APPLICABLE: true
// AFTER-WARNING: Variable 'x' is never used
fun foo() {
    val x = <caret>Box(Any())
}

class Box<T>(t : T) {
    var value = t
}