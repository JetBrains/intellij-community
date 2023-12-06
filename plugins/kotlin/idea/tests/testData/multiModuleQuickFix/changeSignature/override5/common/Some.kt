// "Convert parameter to receiver" "true"
// IGNORE_K2
expect open class A() {
    open fun c(a: Int, b: String)
}

class C : A() {
    override fun c(a: Int, <caret>b: String) {}
}