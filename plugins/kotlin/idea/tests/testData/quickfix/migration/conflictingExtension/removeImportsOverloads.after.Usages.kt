package test

import java.io.File

fun foo(file: File, thread: Thread) {
    print(file.name)
    print(thread.name)
}