// WITH_STDLIB
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

private fun unzip(zip: ZipFile) {
    var errorUnpacking = false
    try {
        zip.let { zipFile ->
            val entries: Enumeration<*> = zipFile.entries()
            while (entries.hasMoreElements()) {
                val nextElement = entries.nextElement()
                nextElement as ZipEntry
            }
        }
        errorUnpacking = false
    }
    finally {
        if (<warning descr="Condition 'errorUnpacking' is always false">errorUnpacking</warning>) {
        }
    }
}
