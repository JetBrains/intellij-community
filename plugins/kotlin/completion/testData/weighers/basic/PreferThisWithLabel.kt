
fun foo(param: Test) {

}

class Test {
    fun String.test(thisOther: Test?) {
        foo(thi<caret>)
    }
}

// ORDER: this@Test
// ORDER: thisOther
// ORDER: this
// IGNORE_K1