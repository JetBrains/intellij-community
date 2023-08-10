// "Create type parameter 'T' in property 'a'" "true"
val T.a: String
    get() {
        val b: T<caret>
        return ""
    }
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createTypeParameter.CreateTypeParameterFromUsageFix