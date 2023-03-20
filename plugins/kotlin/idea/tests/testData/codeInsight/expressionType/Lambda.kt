val x = listOf(1).map {<caret> q -> println(q) }

// TYPE: { q -> println(q) } -> <html>(Int) -&gt; Unit</html>
