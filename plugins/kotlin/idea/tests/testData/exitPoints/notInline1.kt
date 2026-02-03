import javax.swing.SwingUtilities

fun f(a: Int): Int {
    if (a < 5) {
        SwingUtilities.invokeLater(<info descr="null">fun</info> (): Unit {
            <info descr="null">~return</info>
        })
        return 1
    }
    else {
        return 2
    }
}