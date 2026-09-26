fun <T> usedFunctionTypeParameter(t: T) {
    println(t)
}

fun main(args: Array<String>) {
    println(args)
    usedFunctionTypeParameter("")
}