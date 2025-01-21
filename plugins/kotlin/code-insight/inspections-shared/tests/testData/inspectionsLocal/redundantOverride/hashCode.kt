// PROBLEM: none
interface I {
    override fun hashCode(): Int
}

class Test : I {
    <caret>override fun hashCode() = super.hashCode()
}