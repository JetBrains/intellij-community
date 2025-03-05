// FIX: Remove explicit type arguments
// AFTER-WARNING: Variable 'x' is never used
fun foo() {
    val x = Box<caret><Box<String>>(Box("x"))
}

class Box<T>(t : T) {
    var value = t
}