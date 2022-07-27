enum class E {
    A
}

fun use() {
    E.valueOf<caret>("A")
}


//INFO: <div class='definition'><pre>enum class E</pre></div><div class='bottom'><icon src="/org/jetbrains/kotlin/idea/icons/kotlin_file.svg"/>&nbsp;OnEnumValueOfFunction.kt<br/></div>
