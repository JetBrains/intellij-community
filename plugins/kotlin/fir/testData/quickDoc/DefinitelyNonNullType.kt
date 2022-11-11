
fun <T> elvisLike(x: T, y: T & Any): T & Any = x ?: y
fun someFun() {
    elvisLike<String>(<caret>)
}
//INFO: <div class='definition'><pre>fun &lt;T&gt; elvisLike(x: T, y: T!!): T!!</pre></div><div class='bottom'><icon src="file"/>&nbsp;DefinitelyNonNullType.kt<br/></div>
