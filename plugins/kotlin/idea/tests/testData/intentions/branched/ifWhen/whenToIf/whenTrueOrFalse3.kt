fun foo(i : Int) {
    <caret>when (0 == i) {
        true -> 1
        true -> 2
    }
}