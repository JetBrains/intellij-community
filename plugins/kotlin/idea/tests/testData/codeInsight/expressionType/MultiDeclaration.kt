data class IntStringPair(val x: Int, val s: String)

fun f(x: IntStringPair) {
    val (fir<caret>st, second) = x
}

// K1_TYPE: first -> <html>Int</html>

// K2_TYPE: first -> <b>Int</b>
