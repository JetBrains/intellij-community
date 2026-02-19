// PROBLEM: none
package one

class A(x: Double) {
    <caret>constructor(i: Int) : this(i.toDouble())
}


