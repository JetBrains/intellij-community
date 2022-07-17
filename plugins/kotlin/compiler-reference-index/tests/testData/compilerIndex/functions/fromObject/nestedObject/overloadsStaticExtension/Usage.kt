package one.two

fun usage() {
    with(KotlinObject.NestedObject) { Receiver().overloadsStaticExtension(3) }
}