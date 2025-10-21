// FIX: Replace with 'forEach'

fun foo() {
    listOf(1, 2, 3).m<caret>ap<Int, Unit> { print(it) }
}

// IGNORE_K1
