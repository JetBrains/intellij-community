fun foo(i: Int) {
    if (<caret>true) {
        // always true
        val b = 42
        return
    }
}