
interface Foo

fun foo(a: Any) {}

fun Foo.bar() {
    foo(this<caret>)
}

//INFO: <div class='definition'><pre>fun Foo.bar(): Unit</pre></div><div class='bottom'><icon src="file"/>&nbsp;ExtensionReceiverEnd.kt<br/></div>
