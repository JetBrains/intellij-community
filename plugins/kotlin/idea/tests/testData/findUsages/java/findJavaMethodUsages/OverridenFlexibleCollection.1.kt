class K1 : MyJavaCLass() {
    override fun <T> meth(c: Collection<T>?) = Unit
}

class K2 : MyJavaCLass() {
    override fun <T> meth(c: Collection<T>) = Unit
}

class K3 : MyJavaCLass() {
    override fun <T> meth(c: MutableCollection<T>?) = Unit
}

class K4 : MyJavaCLass() {
    override fun <T> meth(c: MutableCollection<T>) = Unit
}
