class FOO {
    var str: String = ""
    operator fun plusAssign(tk: String?) {
        str += tk
    }
}

fun main(args: Array<String?>) {
    val foo: FOO = FOO()
    <caret>if (args.size > 2)
        foo += args[2]
    else
        foo += foo.toString()
}