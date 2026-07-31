// FIX: Convert to collection builder

val x = buildSet<Boolean> {
    add(true)
    addAll(
        listOf(this).flatten() +<caret> this.map { !it } + this.mapTo(this) { !it }
    )
}