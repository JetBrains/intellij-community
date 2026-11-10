val f: () -> Int? = when (true) {
    true -> {<caret>
        l@{ 1 }
    }
    false -> {
        { 2 }
    }
}