package a

class Target

fun Target.plainOne(): String = ""

@JvmName("renamedTwo")
fun Target.two(): String = ""
