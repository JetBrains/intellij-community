// "Assign to property" "true"
// K2_ERROR: 'val' cannot be reassigned.
class Test(var foo: Int) {
    fun test(foo: Int) {
        <caret>foo = foo
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AssignToPropertyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AssignToPropertyFix