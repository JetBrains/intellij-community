// "Create extension function 'Int.!u00A0'" "true"
// WITH_STDLIB

fun test() {
    val t: Int = 1 <caret>`!u00A0` 2
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix