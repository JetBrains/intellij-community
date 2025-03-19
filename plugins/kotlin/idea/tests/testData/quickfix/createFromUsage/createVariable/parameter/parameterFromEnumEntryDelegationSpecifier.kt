// "Create parameter 'x'" "false"
// ERROR: Unresolved reference: x

enum class E(n: Int) {
    X(<caret>x)
}