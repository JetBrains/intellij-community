// "Import extension function 'Companion.foobar'" "true"
package p

open class T1 {
    companion object
    fun T1.Companion.foobar() {}
}

open class T2 : T1()
open class T3 : T2()
object TObject : T3()

fun usage() {
    T1.<caret>foobar()
}
