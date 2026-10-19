// FIR_COMPARISON
// FIR_IDENTICAL
// LANGUAGE_VERSION: 2.5

const val constString: String = ""

fun String.extFun(): String = this

annotation class A(val s: String)

@A(constString.<caret>)
class B

// EXIST: length
// EXIST: {"lookupString":"lowercase","tailText":"() for String in kotlin.text"}
// EXIST: {"lookupString":"uppercase","tailText":"() for String in kotlin.text"}
// EXIST: {"lookupString":"trim","tailText":"() for String in kotlin.text"}
// EXIST: {"lookupString":"trimStart","tailText":"() for String in kotlin.text"}
// EXIST: {"lookupString":"trimEnd","tailText":"() for String in kotlin.text"}
// EXIST: {"lookupString":"trimIndent","tailText":"() for String in kotlin.text"}
// EXIST: trimMargin
// ABSENT: extFun
// ABSENT: hashCode
