// AFTER-WARNING: Parameter 'b' is never used
class Outer {
    class Middle<T> {}
}

class B<T> {}

fun foo(b: B<Outer.Middle<caret><String>>) {
}
