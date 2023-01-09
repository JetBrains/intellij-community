// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve.static

import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.java.beans.PropertyKind
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightClass
import com.intellij.psi.impl.light.LightMethodBuilder
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PropertyUtilBase
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames

/**
 * Serves as a client for PSI infrastructure and as a layer over TOML version catalog files at the same time
 */
class SyntheticVersionCatalogAccessor(project: Project, scope: GlobalSearchScope, model: GradleVersionCatalogModel, className: String) :
  LightClass(JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_OBJECT, scope)!!) {

  private val libraries: Array<PsiMethod> =
    SyntheticAccessorBuilder(project, scope, className, Kind.LIBRARY)
      .buildMethods(this, model.libraries().properties.map(::PropertyModelGraphNode), "")
      .toTypedArray()

  private val plugins: PsiMethod = SyntheticAccessorBuilder(project, scope, className, Kind.PLUGIN)
    .buildEnclosingMethod(this, model.plugins().properties, "plugins")

  private val versions: PsiMethod = SyntheticAccessorBuilder(project, scope, className, Kind.VERSION)
    .buildEnclosingMethod(this, model.versions().properties, "versions")

  private val bundles: PsiMethod = SyntheticAccessorBuilder(project, scope, className, Kind.BUNDLE)
    .buildEnclosingMethod(this, emptyList(), "bundles")

  private val className = "LibrariesFor${StringUtil.capitalize(className)}"

  override fun getMethods(): Array<PsiMethod> {
    return libraries + arrayOf(plugins, versions, bundles)
  }

  override fun getQualifiedName(): String {
    return "org.gradle.accessors.dm.$className"
  }

  override fun getName(): String = className

  companion object {
    private enum class Kind(val prefix: String) {
      LIBRARY("Library"), PLUGIN("Plugin"), BUNDLE("Bundle"), VERSION("Version")
    }

    private class SyntheticAccessorBuilder(val project: Project, val scope: GlobalSearchScope, val className: String, val kind: Kind) {

      private fun buildSyntheticInnerClass(mapping: List<GraphNode>,
                                           containingClass: PsiClass,
                                           name: String): LightClass {
        val clazz = object : LightClass(JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_OBJECT, scope)!!) {
          private val methods = buildMethods(this, mapping, name).toTypedArray()

          override fun getMethods(): Array<out PsiMethod> {
            return methods
          }

          override fun getContainingClass(): PsiClass {
            return containingClass
          }

          override fun getName(): String {
            return name + kind.prefix + "Accessors"
          }

          override fun getQualifiedName(): String {
            return "org.gradle.accessors.dm.LibrariesFor${StringUtil.capitalize(className)}.${name}${kind.prefix}Accessors"
          }
        }

        return clazz
      }

      fun buildMethods(constructedClass: PsiClass, model: List<GraphNode>, prefix: String): List<LightMethodBuilder> {
        val container = mutableListOf<LightMethodBuilder>()
        for (propertyModel in model) {
          val name = propertyModel.getName()
          val getterName = PropertyUtilBase.getAccessorName(name, PropertyKind.GETTER)
          val method = LightMethodBuilder(PsiManager.getInstance(project), JavaLanguage.INSTANCE, getterName)
          method.containingClass = constructedClass
          val innerModel = propertyModel.getChildren()
          if (innerModel != null && !innerModel.containsKey("version") && !innerModel.containsKey("module")) {
            val syntheticClass = buildSyntheticInnerClass(innerModel.values.toList(), constructedClass,
                                                          prefix + StringUtil.capitalize(name))
            method.setMethodReturnType(PsiElementFactory.getInstance(project).createType(syntheticClass, PsiSubstitutor.EMPTY))
          }
          else {
            val fqn = when (kind) {
              Kind.LIBRARY -> GradleCommonClassNames.GRADLE_API_ARTIFACTS_MINIMAL_EXTERNAL_MODULE_DEPENDENCY
              Kind.PLUGIN -> GradleCommonClassNames.GRADLE_PLUGIN_USE_PLUGIN_DEPENDENCY
              Kind.BUNDLE -> GradleCommonClassNames.GRADLE_API_ARTIFACTS_EXTERNAL_MODULE_DEPENDENCY_BUNDLE
              Kind.VERSION -> CommonClassNames.JAVA_LANG_STRING
            }
            val provider = JavaPsiFacade.getInstance(project).findClass(GradleCommonClassNames.GRADLE_API_PROVIDER_PROVIDER, scope)
                           ?: continue
            val minimalDependency = PsiClassType.getTypeByName(fqn, project, scope)
            method.setMethodReturnType(PsiElementFactory.getInstance(project).createType(provider, minimalDependency))
          }
          container.add(method)
        }
        return container
      }

      fun buildEnclosingMethod(constructedClass: PsiClass, model: List<GradlePropertyModel>, enclosingMethodName: String): PsiMethod {
        val accessorName = PropertyUtilBase.getAccessorName(enclosingMethodName, PropertyKind.GETTER)
        val method = LightMethodBuilder(PsiManager.getInstance(project), JavaLanguage.INSTANCE, accessorName)
        method.containingClass = constructedClass
        val graph =
          if (kind == Kind.VERSION) distributeNames(model.map { it.name.split("_", "-") }) ?: emptyList()
          else model.map(::PropertyModelGraphNode)
        val syntheticClass = buildSyntheticInnerClass(graph, constructedClass, "")
        method.setMethodReturnType(PsiElementFactory.getInstance(project).createType(syntheticClass, PsiSubstitutor.EMPTY))
        return method
      }
    }

    private interface GraphNode {
      fun getName(): String
      fun getChildren(): Map<String, GraphNode>?
    }

    private class PropertyModelGraphNode(val model: GradlePropertyModel) : GraphNode {
      override fun getName(): String = model.name

      override fun getChildren(): Map<String, GraphNode>? = model.getValue(
        GradlePropertyModel.MAP_TYPE)?.mapValues { PropertyModelGraphNode(it.value) }
    }

    private fun distributeNames(continuation: List<List<String>>): List<PrefixBasedGraphNode>? {
      if (continuation.isEmpty()) {
        return null
      }
      val grouped = continuation.groupBy({ it[0] }) { it.drop(1).takeIf(List<String>::isNotEmpty) }.mapValues { it.value.filterNotNull() }
      return grouped.map { (name, cont) ->
        val node = distributeNames(cont)
        PrefixBasedGraphNode(name, node)
      }
    }

    private class PrefixBasedGraphNode(val rootName: String, val nested: List<PrefixBasedGraphNode>?) : GraphNode {
      override fun getName(): String = rootName

      override fun getChildren(): Map<String, GraphNode>? = nested?.associateBy { it.rootName }
    }

  }

}