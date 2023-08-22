enum class C : List<*> {
}
fun f() {
  C.val<caret>ues()
}

//INFO: <div class='definition'><pre>enum class C : List&lt;*&gt;</pre></div><div class='bottom'><icon src="file"/>&nbsp;DecompiledSuper.kt<br/></div>
