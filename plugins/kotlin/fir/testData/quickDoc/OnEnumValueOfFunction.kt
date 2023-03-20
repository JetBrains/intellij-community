enum class E {
    A
}

fun use() {
    E.valueOf<caret>("A")
}


//INFO: <div class='definition'><pre>enum class E</pre></div><div class='bottom'><icon src="file"/>&nbsp;OnEnumValueOfFunction.kt<br/></div>
