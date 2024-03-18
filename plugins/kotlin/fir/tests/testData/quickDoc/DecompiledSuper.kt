enum class C : List<*> {
}
fun f() {
  C.val<caret>ues()
}

//INFO: <div class='definition'><pre>enum class C : List&lt;*&gt;</pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Returns an array containing the constants of this enum type, in the order they're declared. This method may be used to iterate over the constants.</p></div><table class='sections'></table><div class='bottom'><icon src="file"/>&nbsp;DecompiledSuper.kt<br/></div>
