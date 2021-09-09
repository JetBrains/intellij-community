// WITH_RUNTIME
fun test1(x: Double) : Boolean = x > 5
fun test2(x: Double) : Boolean = x > 5 && <warning descr="Condition is always true when reached">x > 4</warning>