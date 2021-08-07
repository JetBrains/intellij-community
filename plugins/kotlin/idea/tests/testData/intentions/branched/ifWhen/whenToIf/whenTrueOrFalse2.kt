fun foo(i : Int) {
    <caret>when (0 == i) {
        false -> 1
        true -> 2
    }
}