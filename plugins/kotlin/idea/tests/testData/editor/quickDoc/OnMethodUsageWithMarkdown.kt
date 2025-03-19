/**
 * Some documentation. **Bold** *underline* `code` foo: bar (baz) [quux] <xyzzy> 'apos'
 *
 * [Kotlin](https://www.kotlinlang.org)
 * [a**b**__d__ kas ](https://www.ibm.com)
 *
 * [C]
 *
 * [See **this** class][C]
 *
 * This is _emphasized text_ but text_with_underscores has to preserve the underscores.
 * Single stars embedded in a word like Embedded*Star have to be preserved as well.
 *
 * Exclamation marks are also important! Also in `code blocks!`
 *
 * bt+ : ``prefix ` postfix``
 * backslash: `\`
 */
fun testMethod() {

}

class C {
}

fun test() {
    <caret>testMethod(1, "value")
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">testMethod</span>()<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Some documentation. <strong>Bold</strong> <em>underline</em> <code><span style="">code</span></code> foo: bar (baz) <span style="border:1px solid;border-color:#ff0000;">quux</span>  'apos'</p>
//INFO: <p><a href="https://www.kotlinlang.org">Kotlin</a> <a href="https://www.ibm.com">a<strong>b</strong><strong>d</strong> kas</a></p>
//INFO: <p><a href="psi_element://C"><code><span style="color:#0000ff;">C</span></code></a></p>
//INFO: <p><a href="psi_element://C"><code><span style="color:#0000ff;">See <strong>this</strong> class</span></code></a></p>
//INFO: <p>This is <em>emphasized text</em> but text_with_underscores has to preserve the underscores. Single stars embedded in a word like Embedded*Star have to be preserved as well.</p>
//INFO: <p>Exclamation marks are also important! Also in <code><span style="">code&#32;blocks!</span></code></p>
//INFO: <p>bt+ : <code><span style="">prefix&#32;`&#32;postfix</span></code> backslash: <code><span style="">\</span></code></p></div><table class='sections'></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;OnMethodUsageWithMarkdown.kt<br/></div>
