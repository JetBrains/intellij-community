// "Create type parameter 'test text' in function 'a'" "true"
fun a() {
    val c: `test text`<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createTypeParameter.CreateTypeParameterFromUsageFix