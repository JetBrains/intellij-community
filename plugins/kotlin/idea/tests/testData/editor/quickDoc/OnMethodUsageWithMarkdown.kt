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

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">testMethod</span>()<span style="">: </span><span style="color:#000000;">Unit</span></pre></div><div class='content'><p>Some documentation. <strong>Bold</strong> <em>underline</em> <code><span style="">code</span></code> foo: bar (baz) <a href="psi_element://quux"><code style='font-size:96%;'><span style="color:#000000;">quux</span></code></a>  'apos'</p>
//INFO: <p><a href="https://www.kotlinlang.org">Kotlin</a> <a href="https://www.ibm.com">a<strong>b</strong><strong>d</strong> kas</a></p>
//INFO: <p><a href="psi_element://C"><code style='font-size:96%;'><span style="color:#000000;">C</span></code></a></p>
//INFO: <p><a href="psi_element://C"><code style='font-size:96%;'><span style="color:#000000;">See <strong>this</strong> class</span></code></a></p>
//INFO: <p>This is <em>emphasized text</em> but text_with_underscores has to preserve the underscores. Single stars embedded in a word like Embedded*Star have to be preserved as well.</p>
//INFO: <p>Exclamation marks are also important! Also in <code><span style="">code&#32;blocks!</span></code></p>
//INFO: <p>bt+ : <code><span style="">prefix&#32;`&#32;postfix</span></code> backslash: <code><span style="">\</span></code></p></div><table class='sections'></table>
