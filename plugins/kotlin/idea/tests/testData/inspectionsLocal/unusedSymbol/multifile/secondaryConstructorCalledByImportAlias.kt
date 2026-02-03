// PROBLEM: none
package one

data class Foo(val x: Int) {
    <caret>constructor(x: Int, y: Int) : this(x + y)
}

