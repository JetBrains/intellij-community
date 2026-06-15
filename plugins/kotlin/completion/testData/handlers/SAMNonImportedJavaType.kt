
fun test(): Comparator<String> {
    return <caret>
}

// ELEMENT: Comparator
// TAIL_TEXT: " { T, T -> ... } (function: (T!, T!) -> Int) (kotlin)"
