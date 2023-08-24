// "Create property '!u00A0'" "true"
// ERROR: Property must be initialized
fun test() {
    val t: Int = <caret>`!u00A0`
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix