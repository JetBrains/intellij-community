// PROBLEM: Parameter "b" is never used
val foo: (Int, Int) -> Int = { a, <caret>b ->
    a
}
// IGNORE_K1