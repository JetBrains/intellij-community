// PROBLEM: none

abstract class KK : J() {
    abstract override fun getDescription(): String
}

class K : KK() {
    override fun getDescription() = <caret>Companion.description

    companion object {
        val description = ""
    }
}

//KT-76541
// IGNORE_K2