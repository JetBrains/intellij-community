package org.jetbrains.deft


abstract class Type<T : Obj, B : ObjBuilder<T>>(val id: Int, val base: Type<*, *>? = null) : Obj {
    var open: Boolean = false
    var abstract: Boolean = false
    var sealed: Boolean = false

    val inheritanceAllowed get() = open || sealed || abstract

    val ival: Class<T> get() = javaClass.enclosingClass as Class<T>
    val ivar: Class<B> get() = ival.classes.single { it.simpleName == "Builder" } as Class<B>

    open val packageName: String
        get() = ival.packageName

    override val name by lazy {
        if (ival.enclosingClass == null) ival.simpleName else {
            var topLevelClass: Class<*> = ival
            val outerNames = mutableListOf<String>()
            do {
                outerNames.add(topLevelClass.simpleName)
                topLevelClass = topLevelClass.enclosingClass ?: break
            } while (true)
            outerNames.reversed().joinToString(".")
        }
    }

    protected open fun loadBuilderFactory(): () -> B {
      val ivalClass = ival
      val packageName = ivalClass.packageName
      val simpleName = name.replace(".", "")
      val c = ivalClass.classLoader.loadClass("$packageName.${simpleName}Impl\$Builder")
      val ctor = c.constructors.find { it.parameterCount == 0 }!!
      return { ctor.newInstance() as B }
    }

    private val _builder: () -> B by lazy {
        loadBuilderFactory()
    }

    fun builder(): B = _builder()

    inline fun builder(init: B.() -> Unit): B {
        val builder = builder()
        builder.init()
        return builder
    }

    operator fun invoke(): B = builder()

    inline operator fun invoke(init: B.() -> Unit): T {
        val builder = builder()
        builder.init()
        return builder.build()
    }

    override fun toString(): String = name
}