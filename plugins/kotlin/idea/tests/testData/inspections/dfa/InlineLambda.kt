// WITH_RUNTIME
fun lambdaGlobalReturn(ints: Array<Int>, b : Boolean) {
    var x = 0
    ints.forEach {
        if (b) return
        x++
        print(it)
    }
    if (<warning descr="Condition is always false">b && <warning descr="Condition is always false when reached"><weak_warning descr="Value is always zero">x</weak_warning> == 1</warning></warning>)
        println()
}
fun lambdaGlobalReturn2(ints: Array<Int>, b : Boolean) {
    var x = 0
    ints.forEach {
        x = 1
        if (b) return
        print(it)
    }
    if (<warning descr="Condition is always false">b && <warning descr="Condition is always false when reached"><weak_warning descr="Value is always zero">x</weak_warning> == 1</warning></warning>)
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
    return <warning descr="Condition is always false">x?.let { return x.isEmpty() } ?: false</warning>
}