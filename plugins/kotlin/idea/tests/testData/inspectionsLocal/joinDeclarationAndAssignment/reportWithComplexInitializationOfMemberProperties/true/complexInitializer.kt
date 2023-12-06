class A {
    <caret>val input: String

    init {
        input = if (true) {
            "some string"
        } else {
            "some other string"
        }
    }
}