val x = buildSet<Boolean> {
    add(true)
    addAll(
        listOf(this).flatten() +<caret> this.map { !it } + this.mapTo(this) { !it }
    )
}