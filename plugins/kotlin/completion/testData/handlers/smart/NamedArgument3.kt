fun foo(param1: String, param2: Int, param3: Int) { }

fun bar(pInt: Int) {
    foo("", param2 = 1, param3 = <caret>)
}

// ELEMENT: pInt
