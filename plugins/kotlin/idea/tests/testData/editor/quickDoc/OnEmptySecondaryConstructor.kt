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
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">constructor</span> <span style="color:#000000;">Foo</span>(
//INFO:     <span style="color:#000000;">x</span><span style="">: </span><span style="color:#000000;">Int</span>
//INFO: )</pre></div><div class='content'><p>Documentation is here.</p></div><table class='sections'></table>
