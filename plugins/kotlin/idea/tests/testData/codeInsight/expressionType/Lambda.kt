val x = listOf(1).map {<caret> q -> println(q) }

// K1_TYPE: { q -> println(q) } -> <html>(Int) -&gt; Unit</html>

// K2_TYPE: { q -> println(q) } -> (Int) -&gt; Unit
