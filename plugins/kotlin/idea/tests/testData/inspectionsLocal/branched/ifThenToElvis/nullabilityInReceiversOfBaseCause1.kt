// PROBLEM: none
data class Sas(val f: Int?)
fun Int?.exampleFun() = when (this) {
    null -> "a"
    else -> "b"
}

fun getSas() : Sas? = Sas(null)

fun main() {
    val sas = getSas()
    val str = if (sa<caret>s == null) "null!!!" else sas.f.exampleFun()

    println(str)
}

// IGNORE_K1