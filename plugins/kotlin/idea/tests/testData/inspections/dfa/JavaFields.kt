// WITH_STDLIB
import java.io.File

class JavaFields {
    fun checkSeparatorChar() {
        if (File.separatorChar == '/') {
            if (<warning descr="Condition 'File.separatorChar == '/'' is always true">File.separatorChar == '/'</warning>) {}
        }
    }

    fun testPoint(p: Point) {
        if (p.x == 2) {
            if (<warning descr="Condition 'p.x > 3' is always false">p.x > 3</warning>) {}
        }
    }
}
