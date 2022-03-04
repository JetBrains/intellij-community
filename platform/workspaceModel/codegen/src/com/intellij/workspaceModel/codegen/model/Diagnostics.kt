package storage.codegen.patcher

import java.lang.Appendable

class Diagnostics(val appendable: Appendable? = System.out) {
    fun add(range: SrcRange, message: String) {
        appendable?.appendLine(range.show(message))
    }
}