fun foo() {
    listOf(1, 2, 3, 4, 5).forEach(fun(value: Int) {
        if (value == 3) return@<caret>forEach
        print(value)
    })
    print(" done with anonymous function")
}

//HIGHLIGHTED: fun
//HIGHLIGHTED: return@forEach
