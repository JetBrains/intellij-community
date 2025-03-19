fun checkCloneable(array: Array<String>) {
    array.clone()
}

fun checkSynchronizedIsResolvedInJvm() {
    synchronized(Any()) {
    }
}
