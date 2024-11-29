// "Replace with 'add(this)'" "true"
// WITH_STDLIB
class SomeBuilder() {
    @Deprecated("", ReplaceWith("add(this)"))
    operator fun String?.unaryPlus() {
        TODO()
    }

    fun add(s: String?) {
        TODO()
    }
}

fun someBuild(action: SomeBuilder.() -> Unit) {
    val b = SomeBuilder()
    b.action()
}

fun foo2() {
    someBuild {
        <caret>+"a"
    }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix