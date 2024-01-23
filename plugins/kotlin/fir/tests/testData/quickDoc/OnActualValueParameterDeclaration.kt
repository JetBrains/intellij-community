/**
 * Doc for expected class Foo
 *
 * @param param doc for expected `param` of function `foo`
 */
expect fun foo(param: String)

actual fun foo(pa<caret>ram: String) {}

// IGNORE_K2
//INFO: <div class='definition'><pre>param: String</pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>doc for expected <code><span style=""><span style="">param</span></span></code> of function <code><span style=""><span style="">foo</span></span></code></p></div><table class='sections'></table>
