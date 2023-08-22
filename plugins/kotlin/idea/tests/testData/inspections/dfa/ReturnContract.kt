// WITH_STDLIB
import kotlin.contracts.contract

fun testIsContract(x: Any) {
    val b = testString(x)
    if (b && <warning descr="Condition 'x is String' is always true when reached">x is String</warning>) {
        println()
    }
}

fun testNullOrBlank(str: String?) {
    val b = str.isNullOrBlank()
    if (<warning descr="Condition '!b && str == null' is always false">!b && <warning descr="Condition 'str == null' is always false when reached">str == null</warning></warning>) {
        println()
    }
}

fun testNotNull(x: String?, b: Boolean) {
    if (b) {
        checkNotNull(x)
    }
    if (b) {
        if (<warning descr="Condition 'x == null' is always false">x == null</warning>) {}
    }
}

fun testBoolean(x: Int, y: Double) {
    check(x < 0)
    if (<warning descr="Condition 'x > 0' is always false">x > 0</warning>) {
        println()
    }
    check(y > 0) { "" }
    if (<warning descr="Condition 'y > 0' is always true">y > 0</warning>) {
        println()
    }
}

@OptIn(kotlin.contracts.ExperimentalContracts::class)
fun testString(x: Any):Boolean {
    contract {
          returns(true) implies (x is String)
      }
    return x is String && x.isNotEmpty()
}
