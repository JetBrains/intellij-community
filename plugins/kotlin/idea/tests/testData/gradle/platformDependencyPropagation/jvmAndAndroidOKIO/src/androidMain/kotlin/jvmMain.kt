@file:Suppress("unused", "unused_variable")

import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File

fun androidMain() {
    val somePath: Path = File("sunny").toOkioPath()
    val someFile: File = somePath.toFile()
    val someOtherFile = somePath.myExpectation()
}
