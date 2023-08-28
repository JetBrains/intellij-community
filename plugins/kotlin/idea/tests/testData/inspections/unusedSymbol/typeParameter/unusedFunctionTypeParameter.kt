fun <T> unusedFunctionTypeParameter(p: String) {
    println(p)
}

fun main(args: Array<String>) {
    println(args)
    unusedFunctionTypeParameter("")
}