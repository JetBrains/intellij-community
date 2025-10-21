fun foo() {
    fun fff(p: String, c: Char) {}

    fff(<caret>)
}

// TYPE: "1, "
