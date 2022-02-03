object Main {
    private const val field = 322

    @JvmStatic
    fun main(args: Array<String>) {
        val staticClass = StaticClass()
        val abc = 3
        val bcd = 5 + field
        function(true);
        if (abc < bcd && function(true)) {
            println(abc * bcd - field)
        }
        StaticClass.main(null as Array<String>)
    }

    private fun function(arg: Boolean): Boolean {
        return arg
    }

    private class StaticClass {
        fun cyclesFunction(ere: Int): Int {
            var i = 10
            for (j in 0 until i) {
                val res = j - j + j
                println(res - j)
            }
            i += 10
            while (i > 0) {
                println(i * i)
                i--
            }
            if (true) { i++ }
            return i
        }

        fun stringFunction(sss: String?): String {
            val builder = StringBuilder()
            builder.append("433" + "322" + 'e' + "wrwer")
            val str = builder.toString() + '2' + "123"
            return builder.toString() + "322" + "erer" +
                    "true" + "or false" + str
        }

        companion object {
            @JvmStatic
            fun main(args: Array<String>) {
                function(false)
            }

            private fun function(arg: Boolean): Boolean {
                return arg && function(function && function || arg)
            }

            private const val function = true
        }

        fun selfReturningFunction(arg: Int): StaticClass {
            return this
        }

        var nonStaticField = "123123123"

        init {
            val instance = StaticClass()
            val ss = instance.selfReturningFunction(322).selfReturningFunction(instance.cyclesFunction(0))
                .selfReturningFunction(1).nonStaticField
        }
    }
}

const val globalVariable: Int = 322