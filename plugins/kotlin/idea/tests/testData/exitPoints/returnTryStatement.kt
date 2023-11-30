fun yy(): Int = 5

<info descr="null">fun</info> f(a: Int): Int {
    <info descr="null">return</info>~ try {
        val q = a * a
        <info descr="null">q</info>
    } catch (e: Exception) {
        <info descr="null">0</info>
    } catch (e: Throwable) {
        <info descr="null">-1</info>
    } finally {
      println()
    }
}
