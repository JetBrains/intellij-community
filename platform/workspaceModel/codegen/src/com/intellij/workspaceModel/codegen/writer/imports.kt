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

fun fqn(function: KProperty1<KClass<*>, Collection<Any>>): String = function.fqn
fun fqn1(function: KFunction3<EntityStorage, ConnectionId, WorkspaceEntity, WorkspaceEntity?>): String = function.fqn
fun fqn2(function: KFunction3<EntityStorage, ConnectionId, WorkspaceEntity, Sequence<Any>>): String = function.fqn
fun fqn3(function: KFunction4<EntityStorage, ConnectionId, WorkspaceEntity, WorkspaceEntity?, Unit>): String = function.fqn
fun fqn4(function: KFunction4<EntityStorage, ConnectionId, WorkspaceEntity, List<WorkspaceEntity>, Unit>): String = function.fqn
fun fqn5(function: KFunction4<EntityStorage, ConnectionId, WorkspaceEntity, Sequence<WorkspaceEntity>, Unit>): String = function.fqn
fun fqn6(function: KFunction2<ModifiableWorkspaceEntityBase<*>, MutableEntityStorage, Unit>): String = function.fqn

private val KProperty<*>.fqn: String
  get() {
    val declaringClass = this.getter.javaMethod?.declaringClass ?: return ""
    return fqn(declaringClass.packageName, this.name)
  }

private val KFunction<*>.fqn: String
  get() {
    val declaringClass = this.javaMethod?.declaringClass ?: return ""
    return fqn(declaringClass.packageName, this.name)
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

  return "$fqnEscape$packageName@@$name#$name"
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

class Imports(val scopeFqn: String?) {
  val set = mutableSetOf<String>()

  fun findAndRemoveFqns(str: String): String {
    val res = StringBuilder()
    var p = 0
    while (true) {
      var s = str.indexOf(fqnEscape, p)
      if (s == -1) break
      res.append(str, p, s)
      s += fqnEscape.length
      val e = str.indexOf('#', s)
      check(e != -1)

      val fqn = str.substring(s, e)
      val (packageName, name) = fqn.split("@@")
      add(packageName, name)

      p = e + 1
    }
    res.append(str, p, str.length)
    return res.toString()
  }

  fun add(packageName: String, name: String) {
    if (packageName != scopeFqn) {
      set.add("$packageName.$name")
    }
  }
}

fun fileContents(packageName: String?, code: String, additionalImports: Set<String>? = null): String {
  val imports = Imports(packageName)
  additionalImports?.let { imports.set.addAll(it) }
  val code1 = imports.findAndRemoveFqns(code)

  return buildString {
    if (packageName != null) {
      append("package $packageName\n")
    }
    if (imports.set.isNotEmpty()) {
      append("\n")
      imports.set.sorted().joinTo(this, "\n") { "import $it" }
      append("\n")
    }
    append("\n")
    append(code1)
  }
}