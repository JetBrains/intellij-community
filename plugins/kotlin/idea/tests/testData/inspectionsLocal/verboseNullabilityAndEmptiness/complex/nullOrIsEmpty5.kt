// WITH_STDLIB

fun test(list: List<String>?, flag: Boolean, flag2: Boolean) {
    if (flag && <caret>list != null && list.isNotEmpty() && flag2) println(list.size)
}
