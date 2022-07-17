// WITH_STDLIB
fun itVariable(x:List<Int>) {
    x.forEach {
        if (it > 0) {
            if (<warning descr="Condition 'it < 0' is always false">it < 0</warning>) {
            }
            x.forEach {
                if (it < 0) {}
            }
        }
    }
}

fun itVariableBoxed(x: Long?) {
    x.let { it != null && it < 0 }
}

fun itFlush(x:List<Int>) {
    var y : Int = 1
    x.forEach {
        if (it > 0) {
            if (y == 2) {}
        } else {
            y = 2
        }
    }
}

fun captureMutableLocal() {
    var x = 10
    val fn = {
        if (x == 15) { }
    }
    x = 15
    fn()
}
fun changeLocal() {
    var x = false
    consume { p -> x = p.isEmpty() }
    if (x) {}
}
fun localReturn() {
    var y = 10
    y++
    consume { a ->
        if (a.isEmpty()) return@consume
    }
    if (<warning descr="Condition 'y == 11' is always true">y == 11</warning>) {}
}
fun consume(<warning descr="[UNUSED_PARAMETER] Parameter 'lambda' is never used">lambda</warning>: (String) -> Unit) {}
