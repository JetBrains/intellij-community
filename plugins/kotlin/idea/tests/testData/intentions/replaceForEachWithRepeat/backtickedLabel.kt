// WITH_STDLIB
fun foo() {
    (0..<5).<caret>forEach `foo bar`@{
        if (it == 3) return@`foo bar`
        println(it)
    }
}
