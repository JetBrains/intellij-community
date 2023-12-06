fun foo() {
    if (true) {<caret>
        if (false) {
            foo()
        }
    }
}