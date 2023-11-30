package library
fun f1() {
    SimpleInterface.invoke()
}

open class ClassWithInvoke {
    operator fun invoke() = 1
}

interface SimpleInterface {
    companion object : ClassWithInvoke()
}

