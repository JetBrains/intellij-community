package a

class Target
inline fun Target.withNoinline(noinline action: () -> Unit) = action()