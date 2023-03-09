// PROBLEM: none

fun test(a: Any, b: Any) =
    a is Double && b is Int && a.<caret>equals(b)