fun some() : String? = null

fun test() {
    val <caret>test = some()
}


//INFO: <div class='definition'><pre>val test: String?</pre></div>
