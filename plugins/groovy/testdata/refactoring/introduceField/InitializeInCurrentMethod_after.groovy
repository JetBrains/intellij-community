class X {
    final f

    def foo() {
        if (true) {
            f = "5"
            ptint f<caret>
        }
    }
}
