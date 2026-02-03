class Klass {
    fun unusedFun() {
    }

    @Suppress("unused")
    fun unusedNoWarn() {

    }
}

@Suppress("unused")
class OtherKlass {
    fun unusedNoWarn() {

    }
}

fun main(args: Array<String>) {
    println(args)
    Klass()
    OtherKlass()
}