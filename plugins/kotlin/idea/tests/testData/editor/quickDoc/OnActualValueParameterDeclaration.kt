/**
 * Doc for expected class Foo
 *
 * @param param doc for expected `param` of function `foo`
 */
expect fun foo(param: String)

actual fun foo(pa<caret>ram: String) {}


//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">value-parameter</span> <span style="color:#000000;">param</span><span style="">: </span><span style="color:#000000;">String</span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>doc for expected <code style='font-size:96%;'><span style=""><span style="">param</span></span></code> of function <code style='font-size:96%;'><span style=""><span style="">foo</span></span></code></p></div><table class='sections'></table>
