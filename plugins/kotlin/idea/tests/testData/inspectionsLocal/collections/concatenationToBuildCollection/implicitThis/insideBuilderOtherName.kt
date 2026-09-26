// FIX: Convert to collection builder

val x = buildSet<Boolean> {
    add(true)
    addAll(
        listOf(contains(true)) +<caret> add(add(false)) + (size == 1)
    )
}