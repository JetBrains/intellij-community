import javax.swing.SwingUtilities

<info descr="null">fun</info> f(a: Int): Int {
    if (a < 5) {
        SwingUtilities.invokeLater(fun (): Unit {
            return
        })
        <info descr="null">~return 1</info>
    }
    else {
        <info descr="null">return 2</info>
    }
}