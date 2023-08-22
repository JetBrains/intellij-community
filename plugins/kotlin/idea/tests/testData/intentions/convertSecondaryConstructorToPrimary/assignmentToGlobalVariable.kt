var state: String = "Fail"

class A {
    val p: String

    <caret>constructor(x: String) {
        p = x
        state = x
    }
}
