@file:Suppress("unused")

import platform.posix.fopen

object NativeMain {
    val fromPosix = fopen("my_file", "r")
}
