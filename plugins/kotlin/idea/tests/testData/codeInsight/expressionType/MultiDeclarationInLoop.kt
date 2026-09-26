data class IntStringPair(val x: Int, val s: String)

fun f(x: List<IntStringPair>) {
    for ((fir<caret>st, second) in x) {
    }
}

// K1_TYPE: first -> <html>Int</html>

// K2_TYPE: first -> <b>Int</b>
