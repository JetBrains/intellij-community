package a

class Target
inline fun Target.inlineExt(): String = "inline!"
inline fun <reified T> Target.reifiedExt(): String = T::class.java.name