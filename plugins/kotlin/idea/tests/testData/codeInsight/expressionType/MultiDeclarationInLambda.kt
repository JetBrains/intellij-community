data class IntStringPair(val x: Int, val s: String)

fun f(x: List<IntStringPair>) {
    x.forEach { (fir<caret>st, second) ->
    }
}

// K1_TYPE: first -> <html>Int</html>

// K2_TYPE: first -> <b>Int</b>
// K2_TYPE: (first, second) -> <b>IntStringPair</b>
// K2_TYPE: { (first, second) ->     } -> <b>(IntStringPair) -&gt; Unit</b>
// K2_TYPE: x.forEach { (first, second) ->     } -> <b>Unit</b>
