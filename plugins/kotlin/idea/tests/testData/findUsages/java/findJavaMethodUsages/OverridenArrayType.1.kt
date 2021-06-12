class K1 : MyJavaCLass() {
    override fun arr(i: Array<out Int>?) = Unit
}

class K2 : MyJavaCLass() {
    override fun arr(i: Array<out Int>) = Unit
}

class K3 : MyJavaCLass() {
    override fun arr(i: Array<Int>?) = Unit
}

class K4 : MyJavaCLass() {
    override fun arr(i: Array<Int>) = Unit
}
