fun hintsDemo() {
    listOf(1, 2, 3).filter { elem -> // parameter with inferred type
        elem >= 3
    }
}