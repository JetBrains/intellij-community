class Receiver {
    fun member(first: String, second: Int) {}
}

fun required(first: String, second: Int, third: Boolean = false) {}
fun withVararg(first: String, vararg rest: String, second: Int) {}
fun overloaded(first: String, second: Int) {}
fun overloaded(first: String, second: Int, third: Boolean = false) {}

fun test(receiver: Receiver) {
    required(<hint text="first: TODO(), second: TODO()"/>)
    required(<hint text="first:"/>"value"<hint text=", second: TODO()"/>)
    required(<hint text="first:"/>"value", <hint text="second:"/>1)
    required(second = 1<hint text=", first: TODO()"/>)
    withVararg(<hint text="first: TODO(), second: TODO()"/>)
    receiver.member(<hint text="first: TODO(), second: TODO()"/>)
    overloaded(<hint text="first: TODO(), second: TODO()"/>)
}
