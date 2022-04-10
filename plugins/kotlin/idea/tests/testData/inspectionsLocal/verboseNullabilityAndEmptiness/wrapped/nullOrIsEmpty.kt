// WITH_STDLIB

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.EXPRESSION)
annotation class Anno

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.EXPRESSION)
annotation class Anno2

fun test(list: List<Int>?) {
    if (((<caret>(@Anno label@ list) == null)) || (((@Anno2 label2@ list).isEmpty()))) println(0) else println(list.size)
}
