package a

class A() {
}

class B() {
}

operator fun B.next(): Int = 3

operator fun B.hasNext(): Boolean = false

<selection>operator fun A.iterator() = B()

fun f() {
    for (i in A()) {
    }
}</selection>