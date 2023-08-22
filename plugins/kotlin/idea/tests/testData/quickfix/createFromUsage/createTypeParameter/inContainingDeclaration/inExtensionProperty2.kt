// "Create type parameter 'T' in property 'a'" "true"
class Test {
    val T.a: <caret>T?
        get() = null
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createTypeParameter.CreateTypeParameterFromUsageFix