val x = buildSet<Boolean> {
    add(true)
    addAll(
        listOf(contains(true)) +<caret> add(add(false)) + (size == 1)
    )
}