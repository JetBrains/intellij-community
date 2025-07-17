// WITH_STDLIB
internal fun bug(<warning descr="[EXPOSED_PACKAGE_PRIVATE_TYPE_FROM_INTERNAL_WARNING] 'internal' declaration exposes 'public/*package*/' type 'Storage'. This will become an error in language version 2.4. See https://youtrack.jetbrains.com/issue/KTLC-271.">storage: Storage</warning>) {
    if (storage is FileStorageAnnotation) {
        println(true)
    }
}

fun main() {
    bug(FileStorageAnnotation()) // true
}