// PROBLEM: none
// Issue: KTIJ-31817
// IGNORE_K1

class A {
    var num: Int = 10

    fun test() {
        num = 20

        A().apply {
            num = this@A.num<caret> // not useless
            println(num)
        }
    }
}
