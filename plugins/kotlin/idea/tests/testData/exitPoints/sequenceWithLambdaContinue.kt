// WITH_STDLIB
fun f(){
    val seq = <info descr="null">sequence</info> {
        outer@ for (i in 1..5) {
            for (j in 1..5){
                if (j == 2) continue@outer
                <info descr="null">~yield</info>(i * j) // Highlighted
            }
        }
    }
}