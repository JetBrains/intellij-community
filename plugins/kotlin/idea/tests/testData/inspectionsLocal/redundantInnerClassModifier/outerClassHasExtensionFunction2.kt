// PROBLEM: none
interface Base

val Base.factory: (Int) -> String
    get() = Int::toString

class Outer : Base {
    private i<caret>nner class Inner {
        val value = factory(42)
    }
}