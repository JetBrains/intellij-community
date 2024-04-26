// WITH_STDLIB

val someList = listOf("alpha", "beta").<caret>mapIndexedNotNullTo(destination = hashSetOf()) { index, value -> index + value.length }