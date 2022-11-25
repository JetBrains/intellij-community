package sample

enum class KotlinEnum0 {
    ;
    companion object {
        @JvmStatic
        fun values(arg: Boolean): Array<KotlinEnum0> = emptyArray()
    }
}

enum class KotlinEnum2 {
    ;
    companion object {
        @JvmStatic
        fun values() = 1
    }
}

enum class KotlinEnum3 {
    ONE {
        fun values() = 1
    };
}

enum class KotlinEnum4 {
    ONE {
        override fun values() = 1
    };

    abstract fun values(): Int
}

enum class KotlinEnum5 {
    ;

    fun values() = 1
}

enum class KotlinEnum6 {
    ONE {
        override fun values() = 1
    };

    open fun values() = 2
}