val i: Int = when (true) {
    true -> {<caret>
        { 1 }.invoke()
    }
    false -> 2
}