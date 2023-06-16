package com.intellij.workspaceModel.codegen.impl.writer

import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import kotlin.reflect.*
import kotlin.reflect.jvm.javaMethod

private const val fqnEscape = "#uC03o#"

fun fqn(function: KProperty1<KClass<*>, Collection<Any>>): QualifiedName = function.fqn
fun fqn1(function: KFunction3<EntityStorage, ConnectionId, WorkspaceEntity, WorkspaceEntity?>): QualifiedName = function.fqn
fun fqn2(function: KFunction3<EntityStorage, ConnectionId, WorkspaceEntity, Sequence<Any>>): QualifiedName = function.fqn
fun fqn3(function: KFunction4<EntityStorage, ConnectionId, WorkspaceEntity, WorkspaceEntity?, Unit>): QualifiedName = function.fqn
fun fqn4(function: KFunction4<EntityStorage, ConnectionId, WorkspaceEntity, List<WorkspaceEntity>, Unit>): QualifiedName = function.fqn
fun fqn5(function: KFunction4<EntityStorage, ConnectionId, WorkspaceEntity, Sequence<WorkspaceEntity>, Unit>): QualifiedName = function.fqn
fun fqn6(function: KFunction2<ModifiableWorkspaceEntityBase<*, *>, MutableEntityStorage, Unit>): QualifiedName = function.fqn
fun fqn7(function: KFunction1<Collection<*>, Collection<*>>): QualifiedName = function.fqn

private val KProperty<*>.fqn: QualifiedName
  get() {
    val declaringClass = this.getter.javaMethod?.declaringClass ?: return "".toQualifiedName()
    return fqn(declaringClass.packageName, this.name)
  }

private val KFunction<*>.fqn: QualifiedName
  get() {
    val declaringClass = this.javaMethod?.declaringClass ?: return "".toQualifiedName()
    return fqn(declaringClass.packageName, this.name)
  }

@JvmInline
value class QualifiedName(val encodedString: String) {
  override fun toString(): String {
    return encodedString
  }

  val decoded: String
    get() = encodedString.removePrefix(fqnEscape).substringBefore("#").replace("@@", ".")
  fun appendInner(innerClassPath: String): QualifiedName = QualifiedName("$encodedString.$innerClassPath")
  fun appendSuffix(suffix: String): QualifiedName = QualifiedName("$encodedString$suffix")
} 

/**
 * Temporary string for adding to imports
 */
fun fqn(packageName: String?, name: String): QualifiedName {
  if (packageName.isNullOrEmpty()) return QualifiedName(name)

  val outerClassName = name.substringBefore(".")
  return QualifiedName("$fqnEscape$packageName@@$outerClassName#$name")
}

fun String.toQualifiedName(): QualifiedName {
  val classNameMatch = Regex("\\.[A-Z]").find(this) ?: return QualifiedName(this)
  return fqn(substring(0, classNameMatch.range.first), substring(classNameMatch.range.last))
}

val KClass<*>.fqn: QualifiedName
  get() = java.fqn

val Class<*>.fqn: QualifiedName
  get() {
    val name = name
    val packageName = name.substringBeforeLast(".")
    val className = name.substringAfterLast(".")
      .replace('$', '.')

    if (className.contains(".")) {
      val outerClassName = className.substringBefore(".")
      val innerClassPath = className.substringAfter(".")
      return fqn(packageName, outerClassName).appendInner(innerClassPath)
    }
    else {
      return fqn(packageName, className)
    }
  }
