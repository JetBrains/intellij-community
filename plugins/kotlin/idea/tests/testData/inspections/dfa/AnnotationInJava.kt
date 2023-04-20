// WITH_STDLIB
internal fun bug(storage: Storage) {
    if (storage is FileStorageAnnotation) {
        println(true)
    }
}

fun main() {
    bug(FileStorageAnnotation()) // true
}