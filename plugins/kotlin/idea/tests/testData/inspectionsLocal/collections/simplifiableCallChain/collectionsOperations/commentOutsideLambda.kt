// WITH_STDLIB
val v1 = listOf(1, 2, 3, 11, 33, 25, 100)
    .filter<caret> { it % 2 == 0 } /* Block comment */ // Some Comment
    .isNotEmpty() // Some Additional Comment