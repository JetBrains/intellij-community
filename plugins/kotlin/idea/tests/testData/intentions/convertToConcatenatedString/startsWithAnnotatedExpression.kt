@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.EXPRESSION)
annotation class Foo

fun test(): String {
    return "<caret>${@Foo 1}a"
}
