// FIR_IDENTICAL
@Target(AnnotationTarget.CLASS, AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
annotation class Ann

@Ann class A

fun bar(block: () -> Int) = block()

private
fun foo() {
    1 + @Ann 2

    @Ann 3 + 4

    bar @Ann { 1 }
}
