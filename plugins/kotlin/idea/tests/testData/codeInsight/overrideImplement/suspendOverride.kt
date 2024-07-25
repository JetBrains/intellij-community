// FIR_IDENTICAL
interface A {
    fun a()
}
object AllAtOnce: A {
    @JvmStatic
    <caret>suspend fun anyFun() {}
}