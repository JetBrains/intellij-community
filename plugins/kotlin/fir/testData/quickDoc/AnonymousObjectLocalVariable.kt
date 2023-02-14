interface OurFace
open class OurClass

fun context() {
    val v = object : OurClass(), OurFace {}
    v<caret>
}

//INFO: <div class='definition'><pre>val v: OurClass</pre></div>
