
fun foo(param: Test) {

}

class Test {
    // `this` should be preferred because it matches perfectly, while `thisOther`only matches if we ignore nullability
    fun test(thisOther: Test?) {
        foo(thi<caret>)
    }
}

// ORDER: this
// ORDER: thisOther