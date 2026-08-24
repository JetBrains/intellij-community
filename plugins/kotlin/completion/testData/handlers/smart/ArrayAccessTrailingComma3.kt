class Test

fun test(t: Array<Test>)

fun usage() {
    test(
        [
            Test(),
            Test(),
            T<caret>,
        ]
    )
}

// ELEMENT: Test
// TAIL_TEXT: () (<root>)
