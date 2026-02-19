// WITH_STDLIB

val a = listOf(listOf(1, 2, 3, null)).<caret>mapNotNull { bar ->
    if (bar.isEmpty()) return@mapNotNull 1
    if (bar.size == 2) return@mapNotNull 2
    if (bar.size == 3) return@mapNotNull bar.filter {
        if (it == 5) return@mapNotNull 1
        bar.size == 1
    }.size
    if (bar.size == 4) return@mapNotNull bar.filter mapNotNull@ {
        if (it == 5) return@mapNotNull true
        bar.size == 1
    }.size

    return@mapNotNull bar.mapNotNull {
        return@mapNotNull it
    }
}