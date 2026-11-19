// WITH_STDLIB

val x = listOf(1, 2).flatMap {listOf(it, it + 1, it + 2)}.<caret>filter {true}