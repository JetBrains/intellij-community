// FIX: none
// IGNORE_K1
class Foo {
    fun foo(upper: Int) {
        var target:String = ""
        for (ind<caret>ex in 0 until upper) {
            target = target + "get"
        }
        println(target)
    }
}