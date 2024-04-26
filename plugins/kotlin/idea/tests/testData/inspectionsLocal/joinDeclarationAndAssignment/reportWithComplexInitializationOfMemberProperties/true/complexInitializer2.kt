class A {
    <caret>val input: String

    init {
        input = when {
            true -> {
                "some string"
            }
            else -> {
                "some other string"
            }
        }
    }
}