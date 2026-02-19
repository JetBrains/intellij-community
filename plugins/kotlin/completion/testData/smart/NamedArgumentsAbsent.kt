fun foo(param1: String, param2: Int, param3: Boolean) {}

fun bar(myString: String, myInt: Int, myBool: Boolean) {
    foo(<caret>)
}

// EXIST: myString
// ABSENT: param1 =
// ABSENT: param2 =
// ABSENT: param3 =
