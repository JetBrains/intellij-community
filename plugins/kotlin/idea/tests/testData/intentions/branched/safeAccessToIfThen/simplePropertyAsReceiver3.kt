// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Safe call on a non-null receiver will have nullable type in future releases<br>  Right now safe call on non nullable receiver has not null type: `"hello"?.length` has type Int<br>  In future releases all safe calls will have nullable type: `"hello"?.length` will have type Int?
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
