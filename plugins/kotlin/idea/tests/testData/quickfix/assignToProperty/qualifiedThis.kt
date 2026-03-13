// "Assign to property" "true"
// WITH_STDLIB
// K2_ERROR: 'val' cannot be reassigned.
class Test {
    var foo = 1

    fun test(foo: Int) {
        "".run {
            <caret>foo = foo
        }
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AssignToPropertyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AssignToPropertyFix