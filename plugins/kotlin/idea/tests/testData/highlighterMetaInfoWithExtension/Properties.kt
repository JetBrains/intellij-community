// FIR_IDENTICAL
// WITH_STDLIB
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY

@Target(AnnotationTarget.TYPE)
annotation class MyDynamic

annotation class MyExtension

val composableLambda: @MyDynamic () -> Unit = {}

object Holder {
    @MyExtension
    val value: String = TODO()
}

fun main() {
    composableLambda.invoke()
    composableLambda()
    val v = Holder.value
}