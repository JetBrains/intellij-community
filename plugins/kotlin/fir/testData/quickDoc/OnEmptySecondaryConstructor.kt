/**
Documentation is here.
 */
class Foo {
    private var x: Int = 0

    constructor(x: Int) {
        this.x = x
    } //secondary constructor without documentation
}

fun test() {
    val f = Foo<caret>(10)
}
//INFO: <div class='definition'><pre>constructor(x: Int)</pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Documentation is here.</p></div><table class='sections'></table><div class='bottom'><icon src="class"/>&nbsp;<a href="psi_element://Foo"><code><span style="color:#000000;">Foo</span></code></a><br/></div>
