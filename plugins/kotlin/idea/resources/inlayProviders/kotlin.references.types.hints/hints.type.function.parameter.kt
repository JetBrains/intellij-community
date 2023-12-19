fun hintsDemo() {
    listOf(1, 2, 3).filter { elem /*<# :Int #>*/ ->
        elem >= 3
    }
}
