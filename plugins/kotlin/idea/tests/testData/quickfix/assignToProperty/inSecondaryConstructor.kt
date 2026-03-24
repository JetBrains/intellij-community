// "Assign to property" "true"
// K2_ERROR: 'val' cannot be reassigned.
// K2_ERROR: Property must be initialized or be abstract.
class Test {
    val foo: Int

    constructor(foo: Int) {
        <caret>foo = foo
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AssignToPropertyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AssignToPropertyFix