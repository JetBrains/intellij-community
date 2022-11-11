/**
 * Some documentation
 * on two lines.
 */
fun testMethod() {

}

fun test() {
    <caret>testMethod()
}

//INFO: <div class='definition'><pre>fun testMethod(): Unit</pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Some documentation on two lines.</p></div><table class='sections'></table><div class='bottom'><icon src="file"/>&nbsp;OnMethodUsageMultiline.kt<br/></div>
