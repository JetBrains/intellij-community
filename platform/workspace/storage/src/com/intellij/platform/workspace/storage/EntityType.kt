// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage

/**
 * Base class for companion objects of interfaces extending [WorkspaceEntity]. It is supposed to be used from generated code in entity
 * implementation only.
 */
public abstract class EntityType<T : WorkspaceEntity, B : WorkspaceEntity.Builder<T>>(
  @Deprecated("Has no use, to be removed")
  private val base: EntityType<*, *>? = null
) {
    protected open val entityClass: Class<T>? = null
    protected open val entityImplBuilderClass: Class<*>? = null
  
    private val ival: Class<T> get() = entityClass ?: javaClass.enclosingClass as Class<T>

    private fun loadBuilderFactory(): () -> B {
      val c = entityImplBuilderClass ?: throw RuntimeException("Entity implementation builder class not found for ${ival.simpleName}")
      val ctor = c.constructors.find { it.parameterCount == 0 }!!
      return { ctor.newInstance() as B }
    }

    private val _builder: () -> B by lazy {
        loadBuilderFactory()
    }

    protected fun builder(): B = _builder()
}