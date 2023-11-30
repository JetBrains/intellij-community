package p

interface I1
interface I2
interface I3

interface KotlinInterface<T1, T2>

class KotlinInheritor<T> : KotlinInterface<T, T>

// ALLOW_AST_ACCESS
