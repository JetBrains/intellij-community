// WITH_STDLIB
import kotlin.contracts.*

fun lambdaGlobalReturn(ints: Array<Int>, b : Boolean) {
    var x = 0
    ints.forEach {
        if (b) return
        x++
        print(it)
    }
    if (<warning descr="Condition 'b && x == 1' is always false">b && <warning descr="Condition 'x == 1' is always false when reached"><weak_warning descr="Value of 'x' is always zero">x</weak_warning> == 1</warning></warning>)
        println()
}
fun lambdaGlobalReturn2(ints: Array<Int>, b : Boolean) {
    var x = 0
    ints.forEach {
        x = 1
        if (b) return
        print(it)
    }
    if (<warning descr="Condition 'b && x == 1' is always false">b && <warning descr="Condition 'x == 1' is always false when reached"><weak_warning descr="Value of 'x' is always zero">x</weak_warning> == 1</warning></warning>)
        println()
}
fun lambdaLocalReturn(ints: Array<Int>, b : Boolean) {
    var x = 0
    ints.forEach {
        x = 1
        if (b) return@forEach
        print(it)
    }
    if (b && x == 1)
        println()
}
fun tryInLambda(x : Any) {
    synchronized(x) {
        try {
            println(x)
        }
        catch (ex: Exception) {
            println(ex)
        }
    }
}
fun nestedLambdas(x : Int): Int {
    return x.let { y ->
        y.let {
            if (y > 0) return<warning descr="[LABEL_NAME_CLASH] There is more than one label with such a name in this scope">@let</warning> 10
            if (y < 0) return 15
            return<warning descr="[LABEL_NAME_CLASH] There is more than one label with such a name in this scope">@let</warning> 20
        }
    }
}
fun letInline(x: String?):Boolean {
    // if x is non-null we return from inner condition.
    // as a result, outer one can only evaluate to false.
    // We suppress this
    return x?.let { return x.isEmpty() } ?: false
}
fun exactlyOnce(ints: Array<Int>) {
    var x = 0
    synchronized(ints) {
        x++
    }
    if (<warning descr="Condition 'x == 1' is always true">x == 1</warning>) {}
    synchronized(ints) {
        x++
    }
    if (<warning descr="Condition 'x == 2' is always true">x == 2</warning>) {}
    ints.forEach {
        x++
    }
    if (x == 3) {}
}

fun atMostOnce(result : Result<String>) {
    var x = 1
    result.onSuccess { x++ }
    if (<warning descr="Condition 'x == 0' is always false">x == 0</warning>) {}
    if (x == 1) {}
    if (x == 2) {}
    if (<warning descr="Condition 'x == 1 || x == 2' is always true">x == 1 || <warning descr="Condition 'x == 2' is always true when reached">x == 2</warning></warning>) {}
}

fun atLeastOnce() {
    var x = <warning descr="[VARIABLE_WITH_REDUNDANT_INITIALIZER] Variable 'x' initializer is redundant">1</warning>
    var y = 1
    runAtLeastOnce {
        x = 2
        y++
    }
    if (y == 2) {}
    if (<warning descr="Condition 'x == 2' is always true">x == 2</warning>) {}
}

@<warning descr="[EXPERIMENTAL_IS_NOT_ENABLED] This class can only be used with the compiler argument '-opt-in=kotlin.RequiresOptIn'">OptIn</warning>(kotlin.contracts.ExperimentalContracts::class)
inline fun runAtLeastOnce(lambda: () -> Unit) {
    contract {
        callsInPlace(lambda, InvocationKind.AT_LEAST_ONCE)
    }
    lambda()
    lambda()
}
class NestedLetWithDivision {
    var x: Double? = null
    var y: Int? = null
    var z: Int? = null

    fun test() {
        x?.let { ratio -> y = z?.let { (it / ratio).toInt() } }
    }
}
