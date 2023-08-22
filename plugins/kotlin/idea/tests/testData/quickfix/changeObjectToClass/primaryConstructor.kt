// "Change 'object' to 'class'" "true"
annotation class Ann

// comment
@Ann
object Foo<caret>(val s: String) : Any() {
    constructor() : this("")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeObjectToClassFix