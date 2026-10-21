// FIR_COMPARISON
// FIR_IDENTICAL
// LANGUAGE_VERSION: 2.5

const val constString: String = ""

annotation class A(val s: String)

@A(constString.<caret>)
class B

// EXIST: {"lookupString":"trim","tailText":"() for String in kotlin.text"}
// EXIST: {"lookupString":"uppercase","tailText":"() for String in kotlin.text"}
// ABSENT: {"lookupString":"trim","tailText":" { predicate: (Char) -> Boolean } for String in kotlin.text"}
// ABSENT: {"lookupString":"trim","tailText":"(predicate: (Char) -> Boolean) for String in kotlin.text"}
// ABSENT: {"lookupString":"trimStart","tailText":" { predicate: (Char) -> Boolean } for String in kotlin.text"}
// ABSENT: {"lookupString":"trimStart","tailText":"(predicate: (Char) -> Boolean) for String in kotlin.text"}
// ABSENT: {"lookupString":"trimEnd","tailText":" { predicate: (Char) -> Boolean } for String in kotlin.text"}
// ABSENT: {"lookupString":"trimEnd","tailText":"(predicate: (Char) -> Boolean) for String in kotlin.text"}
// ABSENT: {"lookupString":"lowercase","tailText":"(locale: Locale) for String in kotlin.text"}
// ABSENT: {"lookupString":"uppercase","tailText":"(locale: Locale) for String in kotlin.text"}
