// WITH_STDLIB

val x = hashSetOf("abc").<caret>let {
    it.forEach {
        println(it)
    }
}
