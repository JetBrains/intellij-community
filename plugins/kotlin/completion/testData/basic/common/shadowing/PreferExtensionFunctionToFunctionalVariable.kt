// FIR_COMPARISON
fun Int.xxx() {}
val xxx: Int.() -> Unit
    get() = {}

fun Int.test() = this.xx<caret>

// EXIST: {"lookupString":"xxx","tailText":"() for Int in <root>","typeText":"Unit","icon":"Function"}
// EXIST: {"lookupString":"xxx","tailText":"() (<root>)","typeText":"Unit","icon":"org/jetbrains/kotlin/idea/icons/abstract_extension_function.svg"}