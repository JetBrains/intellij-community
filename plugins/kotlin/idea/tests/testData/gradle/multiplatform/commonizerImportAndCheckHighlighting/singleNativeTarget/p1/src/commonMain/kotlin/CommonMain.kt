@file:Suppress("unused")

import platform.posix.fopen

object CommonMain {
    val fromPosix = fopen("my_file", "r")
}
