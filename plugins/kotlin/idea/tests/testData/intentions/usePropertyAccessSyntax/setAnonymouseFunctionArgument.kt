// IS_APPLICABLE: false
// WITH_STDLIB
fun test() {
    J().<caret>setR(fun() { 
        println("Hello, world!")
    })
}