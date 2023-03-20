// WITH_STDLIB
fun test(set: Set<Int>?) {
    if (<caret>set == null || set.isEmpty()) println(0) else println(set.size)
}
