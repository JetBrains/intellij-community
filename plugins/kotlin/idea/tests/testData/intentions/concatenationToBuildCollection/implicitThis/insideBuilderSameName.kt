val x = buildList<Boolean> {
    add(true)
    addAll(
        listOf(get(0)) +<caret> add(add(false)) + (size == 1)
    )
}