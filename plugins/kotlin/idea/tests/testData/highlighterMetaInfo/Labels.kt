fun bar(block: () -> Int) = block()

fun foo(): Int {
    bar label@ {
        return@label 2
    }

    loop@ for (i in 1..100) {
        break@loop
    }

    loop2@ for (i in 1..100) {
        break@loop2
    }

    return 1
}