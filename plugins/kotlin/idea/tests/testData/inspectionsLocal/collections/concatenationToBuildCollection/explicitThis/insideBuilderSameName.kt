// FIX: Convert to collection builder

val x = buildList<Boolean> {
    add(true)
    addAll(
        this +<caret>  this.map { !it }
    )
}