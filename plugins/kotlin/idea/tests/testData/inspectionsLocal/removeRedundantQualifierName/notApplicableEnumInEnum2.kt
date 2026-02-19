// PROBLEM: none
// WITH_STDLIB
enum class A

enum class B(val x: Int) {
    BB(<caret>A.values().size)
}