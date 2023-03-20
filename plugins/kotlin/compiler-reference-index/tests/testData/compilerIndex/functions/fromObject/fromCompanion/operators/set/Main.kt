class Main {
    companion object {
        operator fun Int.s<caret>et(i: Int, s: String, d: Int) = Unit
    }
}