// WITH_STDLIB

class Test {
    fun a() {
        <caret>throw myError()
    }
}
fun myError() = RuntimeException()