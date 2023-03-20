// WITH_STDLIB
fun foo() {
    run {<caret> /*
      comment
    */
    foo()

    print(1)
}
