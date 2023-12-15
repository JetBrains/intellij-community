enum class E {

}

fun use() {
    E.values<caret>()
}

//INFO: <div class='definition'><pre>enum class E</pre></div><div class='bottom'><icon src="file"/>&nbsp;OnEnumValuesFunction.kt<br/></div>
