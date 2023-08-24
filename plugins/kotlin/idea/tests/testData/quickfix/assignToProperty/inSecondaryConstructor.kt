// "Assign to property" "true"
class Test {
    val foo: Int

    constructor(foo: Int) {
        <caret>foo = foo
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AssignToPropertyFix