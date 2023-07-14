// "Create parameter '!u00A0'" "true"
fun test() {
    val t: Int = <caret>`!u00A0`
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix