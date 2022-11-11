@Deprecated("lol no more mainstream", replaceWith = ReplaceWith(expression = "kek()"))
fun <caret>lol() {
    println("lol")
}

//INFO: <div class='definition'><pre>@Deprecated(message = &quot;lol no more mainstream&quot;, replaceWith = kotlin/ReplaceWith(expression = &quot;kek()&quot;, ))
//INFO: fun lol(): Unit</pre></div><div class='bottom'><icon src="file"/>&nbsp;DeprecationWithReplaceInfo.kt<br/></div>
