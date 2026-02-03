// PROBLEM: none

fun List<Int>.map(transform: (Int) -> Int): List<Int> = listOf(1, 2, 3)

fun foo() {
    listOf(1, 2, 3).m<caret>ap { it * it }
}

// IGNORE_K1
