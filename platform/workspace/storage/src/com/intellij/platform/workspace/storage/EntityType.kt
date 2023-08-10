package com.intellij.platform.workspace.storage

/**
 * Base class for companion objects of interfaces extending [WorkspaceEntity]. It is supposed to be used from generated code in entity
 * implementation only.
 */
abstract class EntityType<T : WorkspaceEntity, B : WorkspaceEntity.Builder<T>>(val base: EntityType<*, *>? = null) {
    var open: Boolean = false
    var abstract: Boolean = false
    var sealed: Boolean = false

    val inheritanceAllowed get() = open || sealed || abstract

    val superTypes: List<EntityType<*, *>>
      get() = base?.superTypes?.toMutableList()?.apply { add(base) } ?: emptyList()


    private val ival: Class<T> get() = javaClass.enclosingClass as Class<T>

    open val packageName: String
        get() = ival.packageName

    open val name by lazy {
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

    protected fun builder(): B = _builder()

    override fun toString(): String = name
}