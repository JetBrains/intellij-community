package companionObjectUsedInJava

class AX826 {
    companion object {
        @JvmField val CONST = 42
    }
}

class BX826 {
    companion object {
        @JvmStatic fun foo() {
        }
    }
}

class CX826 {
    companion object Named {
        @JvmField val CONST = 42
    }
}

class DX826 {
    companion object Named {
        @JvmStatic fun foo() {
        }
    }
}
