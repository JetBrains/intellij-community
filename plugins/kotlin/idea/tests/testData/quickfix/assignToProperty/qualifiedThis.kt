// "Assign to property" "true"
// WITH_STDLIB
class Test {
    var foo = 1

    fun test(foo: Int) {
        "".run {
            <caret>foo = foo
        }
    }
}