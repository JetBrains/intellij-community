fun <T> doSomething(a: T) {}

fun foo() {
    <caret>if (true) doSomething("test") else doSomething("test2")
}
