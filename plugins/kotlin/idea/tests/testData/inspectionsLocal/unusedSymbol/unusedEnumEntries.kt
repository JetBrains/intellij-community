// COMPILER_ARGUMENTS: -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
enum class SomeEnum {
    <caret>UNUSED;

    companion object
}

object Fake {
    val entries: Array<SomeEnum> = arrayOf()
}

val Any.foo get() = Fake

fun foo() {
    SomeEnum.foo.entries
}
