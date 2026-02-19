class K1 : MyJavaCLass() {
    override fun coll(c: Collection<Any?>?) = Unit
}

class K2 : MyJavaCLass() {
    override fun coll(c: Collection<Any?>) = Unit
}

class K3 : MyJavaCLass() {
    override fun coll(c: MutableCollection<Any?>) = Unit
}

class K4 : MyJavaCLass() {
    override fun coll(c: MutableCollection<Any?>?) = Unit
}
