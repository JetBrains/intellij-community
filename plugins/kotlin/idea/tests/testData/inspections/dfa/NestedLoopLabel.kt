// WITH_STDLIB
fun check2NoBlock(
    a: List<Any>,
    b: List<Any>,
    x: Boolean,
    y: Boolean
) {
    outer@
    for (aa in a)
        for (bb in b) {
            if (x) continue
            if (y) continue@outer
            if (<warning descr="Condition 'x' is always false">x</warning>) {}
            if (<warning descr="Condition 'y' is always false">y</warning>) {}
        }
}
fun check3NoBlocks(
    a: List<Any>,
    b: List<Any>,
    c: List<Any>,
    x: Boolean,
    y: Boolean
) {
    outer@
    for (aa in a)
        for (bb in b)
            for (cc in c) {
                if (x) break@outer
                if (y) continue@outer
                if (<warning descr="Condition 'x' is always false">x</warning>) {}
                if (<warning descr="Condition 'y' is always false">y</warning>) {}
            }
}
fun check3Blocks(
    a: List<Any>,
    b: List<Any>,
    c: List<Any>,
    x: Boolean,
    y: Boolean
) {
    outer@
    for (aa in a) {
        println()
        for (bb in b) {
            println()
            for (cc in c) {
                if (x) break@outer
                if (y) continue@outer
                if (<warning descr="Condition 'x' is always false">x</warning>) {}
                if (<warning descr="Condition 'y' is always false">y</warning>) {}
            }
        }
    }
}