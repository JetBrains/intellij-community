// WITH_STDLIB
fun test() {
    listOf(listOf(1, 2, 3)).also<caret> { lists ->
        lists.forEach { inner ->
            inner.forEach { item ->
                if (item == 2) return@forEach
            }
        }
    }
}

