package a

class Target

class Container {
    companion object {
        @JvmName("nestedRenamed")
        fun Target.nested(): String = ""
    }
}
