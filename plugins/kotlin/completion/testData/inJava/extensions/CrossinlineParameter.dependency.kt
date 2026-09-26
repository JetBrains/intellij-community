package a

class Target
inline fun Target.withCrossinline(crossinline action: () -> Unit) = action()