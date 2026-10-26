val x = listOf(1).map {<caret> q -> println(q) }

// K1_TYPE: { q -> println(q) } -> <html>(Int) -&gt; Unit</html>

// K2_TYPE: { q -> println(q) } -> <b>(Int) -&gt; Unit</b>
// K2_TYPE: listOf(1).map { q -> println(q) } -> <b>List&lt;Unit&gt;</b>
