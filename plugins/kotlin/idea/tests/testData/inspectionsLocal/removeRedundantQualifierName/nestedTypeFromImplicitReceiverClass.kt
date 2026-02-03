// PROBLEM: none

class MyBaseClass {
    class Nested
}

fun MyBaseClass.foo() {
    val p: MyBaseClass<caret>.Nested
}
