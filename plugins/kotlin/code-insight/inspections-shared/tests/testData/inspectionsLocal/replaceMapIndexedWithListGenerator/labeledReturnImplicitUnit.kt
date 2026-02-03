// WITH_STDLIB
fun test(list: List<String>) {
    list.mapIn<caret>dexed { index, value ->
        if (index == 0) return@mapIndexed
    }
}
