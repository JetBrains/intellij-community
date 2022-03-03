// "Remove redundant initializer" "true"
// WITH_STDLIB
fun foo() {
    var bar = 1<caret>
    bar = 42
    println(bar)
}