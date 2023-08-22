package one.two

fun usage2() {
    with(KotlinObject.NestedObject) { Receiver().overloadsStaticExtension() }
}