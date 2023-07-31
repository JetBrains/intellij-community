val Any?.foo: Int
    <info descr="null">get</info>() {
        this?.let {
            <info descr="null">~return 1</info>
        }
        <info descr="null">return 2</info>
    }

public inline fun <T> T.let(block: (T) -> Unit) {}