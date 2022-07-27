fun test() {
    listOf(1, 2, 4).<caret>filter { it > 0 }
}

//INFO: <div class='definition'><pre>inline fun &lt;T&gt; Iterable&lt;T&gt;.filter(predicate: (T) -&gt; Boolean): List&lt;T&gt;</pre></div><div class='bottom'><icon src="class"/>&nbsp;<a href="psi_element://kotlin.collections"><code><span style="color:#000000;">kotlin.collections</span></code></a><br/><icon src="file"/>&nbsp;CollectionsKt.class<br/></div>
