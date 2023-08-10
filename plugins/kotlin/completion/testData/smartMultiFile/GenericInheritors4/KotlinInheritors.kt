package p

interface I1<T>
interface I2

interface KotlinInterface<T>

class KotlinInheritor<T> : KotlinInterface<I1<T>>

// ALLOW_AST_ACCESS
