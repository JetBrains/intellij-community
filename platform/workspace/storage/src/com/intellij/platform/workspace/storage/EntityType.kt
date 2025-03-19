// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage

/**
 * Base class for companion objects of interfaces extending [WorkspaceEntity]. It is supposed to be used from generated code in entity
 * implementation only.
 */
public abstract class EntityType<T : WorkspaceEntity, B : WorkspaceEntity.Builder<T>>(
  private val base: EntityType<*, *>? = null, // TODO: Do we need base?
) {
    private val ival: Class<T> get() = javaClass.enclosingClass as Class<T>

    private val name: String by lazy {
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

    private fun loadBuilderFactory(): () -> B {
      val ivalClass = ival
      val packageName = ivalClass.packageName
      val simpleName = name.replace(".", "")
      val c = ivalClass.classLoader.loadClass("$packageName.impl.${simpleName}Impl\$Builder")
      val ctor = c.constructors.find { it.parameterCount == 0 }!!
      return { ctor.newInstance() as B }
    }

    private val _builder: () -> B by lazy {
        loadBuilderFactory()
    }

    protected fun builder(): B = _builder()
}