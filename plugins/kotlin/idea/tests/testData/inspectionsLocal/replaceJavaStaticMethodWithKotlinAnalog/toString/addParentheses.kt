// WITH_STDLIB

fun test(b: Boolean) {
    Integer.toString<caret>(if (b) 1 else 0)
}