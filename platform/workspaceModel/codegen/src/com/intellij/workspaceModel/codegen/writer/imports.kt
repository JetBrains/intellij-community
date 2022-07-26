package com.intellij.workspaceModel.codegen.utils

import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import org.jetbrains.deft.Obj
import kotlin.reflect.*
import kotlin.reflect.jvm.javaMethod

val fqnEscape = "#uC03o#"

fun fqn1(function: KFunction3<EntityStorage, ConnectionId, WorkspaceEntity, WorkspaceEntity?>): String = function.fqn
fun fqn2(function: KFunction3<EntityStorage, ConnectionId, WorkspaceEntity, Sequence<Any>>): String = function.fqn
fun fqn3(function: KFunction4<EntityStorage, ConnectionId, WorkspaceEntity, WorkspaceEntity?, Unit>): String = function.fqn
fun fqn4(function: KFunction4<EntityStorage, ConnectionId, WorkspaceEntity, List<WorkspaceEntity>, Unit>): String = function.fqn
fun fqn5(function: KFunction4<EntityStorage, ConnectionId, WorkspaceEntity, Sequence<WorkspaceEntity>, Unit>): String = function.fqn

private val KProperty<*>.fqn: String
  get() {
    val declaringClass = this.getter.javaMethod?.declaringClass ?: return ""
    return fqn(declaringClass.packageName, this.name)
  }

private val KFunction<*>.fqn: String
  get() {
    val declaringClass = this.javaMethod?.declaringClass ?: return ""
    require(declaringClass.packageName == "com.intellij.workspaceModel.storage.impl")
    return name
  }

fun wsFqn(name: String): String {
  val packageName = when (name) {
    "VirtualFileUrl" -> "com.intellij.workspaceModel.storage.url"
    "EntitySource", "referrersx", "referrersy"-> "com.intellij.workspaceModel.storage"
    else -> null
  }

  return fqn(packageName, name)
}

/**
 * Temporary string for adding to imports
 */
fun fqn(packageName: String?, name: String): String {
  if (packageName == null) return name

  return "$packageName.$name"
}

val KClass<*>.fqn: String
  get() = java.fqn

val Class<*>.fqn: String
  get() {
    val name = name
    val packageName = name.substringBeforeLast(".")
    val className = name.substringAfterLast(".")
      .replace('$', '.')

    if (className.contains(".")) {
      val outerClassName = className.substringBefore(".")
      val innerClassPath = className.substringAfter(".")
      return fqn(packageName, outerClassName) + "." + innerClassPath
    }
    else {
      return fqn(packageName, className)
    }
  }