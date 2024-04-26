// WITH_STDLIB
// PROBLEM: none
open class K {
    open fun ccccc(c<caret>cc: Int) {}
    // to make ccccc used
    override fun toString(): String {
       ccccc(0)
       return super.toString()
    }
}
class KImpl : K() {
   override fun ccccc(ccc: Int) {
      println(ccc.hashCode())
   }
}