open class Parameterized<A, C: Cloneable, R: Runnable>

class AC
class CC : Cloneable
class RC : Runnable {
    override fun run() {}
}

class UsageClass : Parameterized<AC, CC<caret>, RC>()

