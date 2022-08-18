/**
 * @constructor constructor tag description
 * @property s property description
 * @param s param description
 */
class Foo(val s: String) {
}

fun usage() {
    // should display @constructor content and @param content.
    // @property is there to be a divider in sections between @constructor and @param,
    // it allows these two tags to be put in two different sections, thus testing expected behaviour
    // (it should additionally find sections with @param regardless of position)
    val foo = F<caret>oo("argument")
}

//INFO: <div class='definition'><pre>constructor(s: String)</pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>constructor tag description</p></div><table class='sections'><tr><td valign='top' class='section'><p>Params:</td><td valign='top'><p><code><a href="psi_element://s"><code style='font-size:96%;'><span style="color:#660e7a;font-weight:bold;">s</span></code></a></code> - param description</td><tr><td valign='top' class='section'><p>Properties:</td><td valign='top'><p><code><a href="psi_element://s"><code style='font-size:96%;'><span style="color:#660e7a;font-weight:bold;">s</span></code></a></code> - property description</td></table><div class='bottom'><icon src="class"/>&nbsp;<a href="psi_element://Foo"><code><span style="color:#000000;">Foo</span></code></a><br/></div>
