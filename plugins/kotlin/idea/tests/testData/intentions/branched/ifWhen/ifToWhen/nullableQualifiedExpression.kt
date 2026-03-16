// WITH_STDLIB
class MyClass(val property: String)

fun test(obj: MyClass?) {
    i<caret>f (obj?.property == "a") {
        println("a")
    } else if (obj?.property == "b") {
        println("b")
    } else {
        println("other")
    }
}
