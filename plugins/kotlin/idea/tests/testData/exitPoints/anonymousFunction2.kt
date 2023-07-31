fun foo() {
    listOf(1, 2, 3, 4, 5).forEach(<info descr="null">fun</info>(value: Int) {
        if (value == 3) <info descr="null">return@~forEach</info>
        print(value)
    })
    print(" done with anonymous function")
}