fun test(): (Int) -> Int {
    return if (true) {
        { <caret>_ -> 42 }
    } else {
        { _ -> 42 }
    }
}
// PROBLEM: none