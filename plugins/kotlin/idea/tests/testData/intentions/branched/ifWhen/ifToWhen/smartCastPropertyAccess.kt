// WITH_STDLIB
class MyClass(val property: String)

fun test(obj: Any) {
    i<caret>f (obj is MyClass && obj.property == "a") {
        println("a")
    } else if (obj is MyClass && obj.property == "b") {
        println("b")
    } else {
        println("other")
    }
}
