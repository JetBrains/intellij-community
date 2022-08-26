
interface Foo

fun foo(a: Any) {}

fun Foo.bar() {
    foo(th<caret>is)
}

//INFO: <div class='definition'><pre>fun Foo.bar(): Unit</pre></div><div class='bottom'><icon src="file"/>&nbsp;ExtensionReceiver.kt<br/></div>
