package a

class Target

@JvmName("renamedPrivate")
private fun Target.secret(): String = ""
