package p2

interface KotlinInterface

open class KotlinInheritor1 : KotlinInterface

class KotlinInheritor2(s: String) : KotlinInheritor1()

abstract class KotlinInheritor3 : KotlinInterface

class C {
    private class PrivateInheritor : KotlinInterface
}

object ObjectInheritor : KotlinInterface

// ALLOW_AST_ACCESS
