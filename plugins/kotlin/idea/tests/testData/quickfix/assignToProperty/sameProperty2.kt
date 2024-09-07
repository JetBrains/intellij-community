// "Assign to property" "true"
class Test {
    var foo = 1

    fun test(foo: String) {
        <caret>foo = 2
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AssignToPropertyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AssignToPropertyFix