// WITH_STDLIB

import java.util.jar.JarFile

class JavaConstant {
    fun test(x: String) {
        if (x == JarFile.MANIFEST_NAME) {
            if (<warning descr="Condition 'x == \"META-INF/MANIFEST.MF\"' is always true">x == "META-INF/MANIFEST.MF"</warning>) {}
        }
    }
}
