fun foo() {
    myMap {<caret> it: Int -> it }
}

fun <T> myMap(transform: (T) -> T): T = TODO()

// TYPE: { it: Int -> it } -> <html>(Int) -&gt; Int</html>