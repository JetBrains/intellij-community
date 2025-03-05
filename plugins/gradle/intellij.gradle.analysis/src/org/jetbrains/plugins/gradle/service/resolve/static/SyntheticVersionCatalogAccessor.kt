// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve.static

import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.java.beans.PropertyKind
import com.intellij.openapi.diagnostic.thisLogger
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
internal class SyntheticVersionCatalogAccessor(
  project: Project,
  scope: GlobalSearchScope,
  model: GradleVersionCatalogModel,
  catalogName: String,
  delegate: PsiClass,
) : LightClass(delegate) {

  private val libraries: Array<PsiMethod> =
    SyntheticAccessorBuilder(project, scope, Kind.LIBRARY)
      .buildMethods(this, model.libraries().properties.let(::assembleTree), "")
      .toTypedArray()

  private val plugins: PsiMethod = SyntheticAccessorBuilder(project, scope, Kind.PLUGIN)
    .buildEnclosingMethod(this, model.plugins().properties, "plugins")

  private val versions: PsiMethod = SyntheticAccessorBuilder(project, scope, Kind.VERSION)
    .buildEnclosingMethod(this, model.versions().properties, "versions")

  private val bundles: PsiMethod = SyntheticAccessorBuilder(project, scope, Kind.BUNDLE)
    .buildEnclosingMethod(this, model.bundles().properties, "bundles")

  private val className = "LibrariesFor${StringUtil.capitalize(catalogName)}"

  override fun getMethods(): Array<PsiMethod> {
    return libraries + arrayOf(plugins, versions, bundles)
  }

  override fun getQualifiedName(): String {
    return "org.gradle.accessors.dm.$className"
  }

  override fun getName(): String = className

  companion object {

    fun create(
      project: Project,
      scope: GlobalSearchScope,
      model: GradleVersionCatalogModel,
      className: String,
    ): SyntheticVersionCatalogAccessor? {
      val delegate = JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_OBJECT, scope) ?: return null
      return SyntheticVersionCatalogAccessor(project, scope, model, className, delegate)
    }

    private enum class Kind(val prefix: String) {
      LIBRARY("Library"), PLUGIN("Plugin"), BUNDLE("Bundle"), VERSION("Version")
    }

    private class SyntheticAccessorBuilder(val project: Project, val gradleScope: GlobalSearchScope, val kind: Kind) {

      private fun buildSyntheticInnerClass(mapping: List<Tree>,
                                           containingClass: PsiClass,
                                           name: String,
                                           asProviderType: PsiClassType?): LightClass {
        val factoryClass = JavaPsiFacade.getInstance(project).findClass("org.gradle.api.internal.catalog.ExternalModuleDependencyFactory", gradleScope)
        val innerClassName = when(kind) {
          Kind.LIBRARY -> "DependencyNotationSupplier"
          Kind.PLUGIN -> "PluginNotationSupplier"
          Kind.BUNDLE -> "BundleNotationSupplier"
          Kind.VERSION -> "VersionNotationSupplier"
        }
        val stubClass = if (asProviderType != null) factoryClass?.innerClasses?.find { it.name == innerClassName } else factoryClass
        val actualStub = stubClass ?: JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_OBJECT, gradleScope)!!
        val clazz = object : LightClass(actualStub) {
          private val methods = buildMethods(this, mapping, name).let { addAsProviderMethod(it, this) }.toTypedArray()

          fun addAsProviderMethod(list: List<LightMethodBuilder>, container: PsiClass) : List<LightMethodBuilder> {
            if (asProviderType == null) {
              return list
            }
            val method = LightMethodBuilder(containingClass.manager, JavaLanguage.INSTANCE, "asProvider")
            method.setMethodReturnType(asProviderType)
            method.containingClass = container
            return list + method
          }

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
            return "org.gradle.accessors.dm.LibrariesFor${StringUtil.capitalize(innerClassName)}.${name}${kind.prefix}Accessors"
          }
        }

        return clazz
      }

      fun buildMethods(constructedClass: PsiClass, model: List<Tree>, prefix: String): List<LightMethodBuilder> {
        val container = mutableListOf<LightMethodBuilder>()
        for (modelTree in model) {
          val name = modelTree.labelName
          val getterName = PropertyUtilBase.getAccessorName(name, PropertyKind.GETTER)
          val method = LightMethodBuilder(PsiManager.getInstance(project), JavaLanguage.INSTANCE, getterName)
          method.containingClass = constructedClass

          val providerType = if (modelTree.root != null) {
            val fqn = when (kind) {
              Kind.LIBRARY -> GradleCommonClassNames.GRADLE_API_ARTIFACTS_MINIMAL_EXTERNAL_MODULE_DEPENDENCY
              Kind.PLUGIN -> GradleCommonClassNames.GRADLE_PLUGIN_USE_PLUGIN_DEPENDENCY
              Kind.BUNDLE -> GradleCommonClassNames.GRADLE_API_ARTIFACTS_EXTERNAL_MODULE_DEPENDENCY_BUNDLE
              Kind.VERSION -> CommonClassNames.JAVA_LANG_STRING
            }
            val provider = JavaPsiFacade.getInstance(project).findClass(GradleCommonClassNames.GRADLE_API_PROVIDER_PROVIDER, gradleScope)
                           ?: continue
            val minimalDependency = PsiClassType.getTypeByName(fqn, project, gradleScope)
            PsiElementFactory.getInstance(project).createType(provider, minimalDependency)
          } else {
            null
          }

          val innerModel = modelTree.children
          if (innerModel.isNotEmpty()) {
            val syntheticClass = buildSyntheticInnerClass(innerModel, constructedClass, prefix + StringUtil.capitalize(name), providerType)
            method.setMethodReturnType(PsiElementFactory.getInstance(project).createType(syntheticClass, PsiSubstitutor.EMPTY))
          }
          else {
            method.setMethodReturnType(providerType)
          }
          container.add(method)
        }
        return container
      }

      fun buildEnclosingMethod(constructedClass: PsiClass, model: List<GradlePropertyModel>, enclosingMethodName: String): PsiMethod {
        val accessorName = PropertyUtilBase.getAccessorName(enclosingMethodName, PropertyKind.GETTER)
        val method = LightMethodBuilder(PsiManager.getInstance(project), JavaLanguage.INSTANCE, accessorName)
        method.containingClass = constructedClass
        val graph= assembleTree(model)
        val syntheticClass = buildSyntheticInnerClass(graph, constructedClass, "", null)
        method.setMethodReturnType(PsiElementFactory.getInstance(project).createType(syntheticClass, PsiSubstitutor.EMPTY))
        return method
      }
    }

    private data class Tree(val labelName: String, val root: GradlePropertyModel?, val children: List<Tree>)

    private fun assembleTree(properties: List<GradlePropertyModel>) : List<Tree> {

      fun assembleTreeLocally(uncompressed: List<Pair<IdentifierPath, GradlePropertyModel>>) : List<Tree> {
        val result = mutableListOf<Tree>()
        val initialPrefixes = uncompressed.groupBy { (path, _) -> path[0] }
        for ((rootLabel, matching) in initialPrefixes) {
          val rest = matching.map { (path, model) -> path.drop(1) to model }
          val (leaves, nodes) = rest.partition { (path, _) -> path.isEmpty() }
          if (leaves.size >= 2) {
            thisLogger().error("There should be only one leaf in version catalog tree : $properties")
          }
          val nestedTrees = assembleTreeLocally(nodes)
          result.add(Tree(rootLabel, leaves.singleOrNull()?.second, nestedTrees))
        }
        return result
      }

      val uncompressedPropertiesMapping = properties.map { it.name.split(Regex("[-_]")) to it }

      return assembleTreeLocally(uncompressedPropertiesMapping)
    }
  }

}

private typealias IdentifierPath = List<String>