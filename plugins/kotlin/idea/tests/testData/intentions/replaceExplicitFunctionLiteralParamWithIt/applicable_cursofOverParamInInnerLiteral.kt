// AFTER-WARNING: Parameter 'y' is never used, could be renamed to _
fun foo(a: (Int) -> Int): Int = a(1)
val x = foo { p -> foo { y -> <caret>p } }