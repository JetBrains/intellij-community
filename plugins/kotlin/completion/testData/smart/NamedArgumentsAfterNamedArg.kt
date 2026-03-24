fun foo(param1: String, param2: Int, param3: Boolean) {}

fun bar(myString: String, myInt: Int, myBool: Boolean) {
    foo(param1 = "test", <caret>)
}

// EXIST: myInt
// ABSENT: param1 =
// ABSENT: param2 =
// ABSENT: param3 =

// It could be reconsidered if we want to show named arguments in smart completion in this case for K2