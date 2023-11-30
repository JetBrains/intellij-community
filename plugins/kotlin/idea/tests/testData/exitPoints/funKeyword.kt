<info descr="null">~fun</info> test(s: String?): Int {
    if (s != null) {
        <info descr="null">return@test 1</info>
    }
    <info descr="null">return 0</info>
}