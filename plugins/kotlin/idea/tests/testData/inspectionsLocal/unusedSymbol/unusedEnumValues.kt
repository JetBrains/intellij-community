enum class SomeEnum {
    <caret>UNUSED;

    companion object
}

object Fake {
    fun values(): Array<SomeEnum> = arrayOf()
}

val Any.foo get() = Fake

fun foo() {
    SomeEnum.foo.values()
}
