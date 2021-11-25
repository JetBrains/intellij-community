// PROBLEM: none
// WITH_STDLIB

val xx = with(fu<caret>n(x: Int, y: Int) = x + y) {
    invoke(1, 2)
}