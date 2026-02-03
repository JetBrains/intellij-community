// WITH_STDLIB

class C {
    val c = "abc".<caret>let {
        println(it + this)
    }
}
