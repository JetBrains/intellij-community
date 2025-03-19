class ProducerApi {
    fun sayHello() = println("Hello")
}

inline fun inlineMe(action: () -> Unit) {
    println("Action")
    action()
}