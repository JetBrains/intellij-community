// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Unnecessary safe call on a non-null receiver of type B?
// AFTER-WARNING: Variable 'x' is never used
class B {
    val c = C()
}

class C {
    val d = "bc"
}

fun main(args: Array<String>) {
    val a: B? = B()
    val x = a?.c?.<caret>d
}
