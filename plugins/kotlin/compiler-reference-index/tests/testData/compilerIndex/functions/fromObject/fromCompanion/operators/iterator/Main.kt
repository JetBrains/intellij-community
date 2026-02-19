class Main {
    companion object {
        operator fun Int.it<caret>erator(): Iterator<String> = TODO()
    }
}
