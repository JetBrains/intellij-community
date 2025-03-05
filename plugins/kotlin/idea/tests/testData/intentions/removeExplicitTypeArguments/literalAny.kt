// FIX: Remove explicit type arguments
// AFTER-WARNING: Variable 'x' is never used
fun foo() {
    val x = Box<caret><Any>(Any())
}

class Box<T>(t : T) {
    var value = t
}