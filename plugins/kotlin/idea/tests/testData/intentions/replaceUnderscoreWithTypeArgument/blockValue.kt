fun f(p: Int): List<String>? {
    return if (p > 0) {
        print("a")
        listOf<<caret>_>()
    }
    else {
        null
    }
}

// WITH_STDLIB