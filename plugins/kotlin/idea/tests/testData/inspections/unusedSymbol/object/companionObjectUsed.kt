class A {
    companion object {
    }
}

class B {
    companion object Named {
    }
}

fun main(args: Array<String>) {
    println(args)
    val a = A
    B.Named
}