fun test() {
    val c = MyClass()
    c.<warning descr="[DEPRECATION]">test1</warning>
    c.<warning descr="[DEPRECATION]">test2</warning>
    c.test2 = ""

    c.<warning descr="[DEPRECATION]">test3</warning>
}

class MyClass() {
    public val test1: String = ""
      @Deprecated("Use A instead") get

    public var test2: String = ""
      @Deprecated("Use A instead") get

    @Deprecated("Use A instead") public val test3: String = ""
      @Deprecated("Use A instead") get
}

// NO_CHECK_INFOS
// NO_CHECK_WEAK_WARNINGS
