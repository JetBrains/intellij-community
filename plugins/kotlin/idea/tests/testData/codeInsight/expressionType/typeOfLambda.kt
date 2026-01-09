fun foo() {
    myMap {<caret> it: Int -> it }
}

fun <T> myMap(transform: (T) -> T): T = TODO()

// K1_TYPE: { it: Int -> it } -> <html>(Int) -&gt; Int</html>

// K2_TYPE: { it: Int -> it } -> <b>(Int) -&gt; Int</b>