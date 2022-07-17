// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Variable 'x' is never used
class B {
    val c = C()
}

class C {
    val d = "bc"
}

fun main(args: Array<String>) {
    val a: B? = B()
    val x = a<caret>?.c?.d
}

