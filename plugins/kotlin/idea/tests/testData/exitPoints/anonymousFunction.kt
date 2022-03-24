fun foo() {
    listOf(1, 2, 3, 4, 5).forEach(fun(value: Int) {
        if (value == 3) <caret>return
        print(value)
    })
    print(" done with anonymous function")
}

//HIGHLIGHTED: fun
//HIGHLIGHTED: return
