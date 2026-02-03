// "Create label outer@" "false"
// WITH_STDLIB
fun test(): Int {
    val list = listOf(1, 2, 3)

    list.forEach outer@{ item ->
        list.forEach { nested ->
            if (nested > 2)<caret>return@outer
        }
    }
    return 0
}