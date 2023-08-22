// WITH_STDLIB

fun foo() {
    val c: Collection<String> = listOf("")
    c.size<caret> > 0
}
