// WITH_STDLIB
fun unboxProperty(value: Int): Boolean =
    MyObj.test == null || MyObj.test >= value

fun unboxProperty2(value: Int): Boolean =
    MyObj.test == null || <warning descr="Condition 'MyObj.test >= value && value > 5 && MyObj.test < 5' is always false when reached">MyObj.test >= value && value > 5 && <warning descr="Condition 'MyObj.test < 5' is always false when reached">MyObj.test < 5</warning></warning>

object MyObj {
    val test: Int? = if (Math.random() > 0.5) null else 1
}

fun unboxPropertyUnnamedCompanion(value: Int): Boolean =
    MyObj1.Companion.test == null || MyObj1.Companion.test >= value

class MyObj1 {
    companion object {
        val test: Int? = if (Math.random() > 0.5) null else 1
    }
}

fun unboxPropertyNamedCompanion(value: Int): Boolean =
    MyObj2.Name.test == null || MyObj2.Name.test >= value

class MyObj2 {
    companion object Name {
        val test: Int? = if (Math.random() > 0.5) null else 1
    }
}

fun test(x : Int?) {
    if (x != null && x > 5) {}
    if (<warning descr="Condition 'x != null && x > 5 && x < 3' is always false">x != null && x > 5 && <warning descr="Condition 'x < 3' is always false when reached">x < 3</warning></warning>) {}
}
fun test(y : Int, z: Int) {
    var x : Int? = null
    if (z == 2) x = y
    if (<warning descr="Condition 'z == 2 && x == null' is always false">z == 2 && <warning descr="Condition 'x == null' is always false when reached">x == null</warning></warning>) {}
}
fun test(x:Int?, y:Int) {
    if (x != null && y >= x) { }
}
fun elvis(x: Int?) {
    val y = x ?: 2
    if (x == null) {
        if (<warning descr="Condition 'y == 2' is always true">y == 2</warning>) {

        }
    }
}