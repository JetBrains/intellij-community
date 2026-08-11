// "Change 'object' to 'class'" "true"
// K2_ERROR: CONSTRUCTOR_IN_OBJECT
// K2_ERROR: CONSTRUCTOR_IN_OBJECT
// K2_ERROR: NONE_APPLICABLE
// K2_ERROR: UNRESOLVED_REFERENCE
annotation class Ann

// comment
@Ann
object Foo<caret>(val s: String) : Any() {
    constructor() : this("")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeObjectToClassFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeObjectToClassFix