// WITH_STDLIB

fun test() {
    java.util.concurrent.atomic.AtomicReference(listOf(1, 2, 3)).get().size<caret> > 0
}