// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.testing

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.junit2.PsiMemberParameterizedLocation
import com.intellij.execution.junit2.info.MethodLocation
import com.intellij.execution.testframework.JavaTestLocator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.classes.KtFakeLightMethod
import org.jetbrains.kotlin.asJava.toFakeLightClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.base.facet.isNewMultiPlatformModule
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestLocationInfo
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestLocatorExtension
import org.jetbrains.plugins.gradle.util.createTestFilterFrom

internal class KotlinMppGradleTestLocator : GradleTestLocatorExtension, DumbAware {
    override fun getLocation(
        protocol: String,
        path: String,
        metainfo: String?,
        project: Project,
        scope: GlobalSearchScope,
    ): List<Location<*>> {
        if (protocol != JavaTestLocator.SUITE_PROTOCOL && protocol != JavaTestLocator.TEST_PROTOCOL) {
            return emptyList()
        }

        return try {
            DumbService.getInstance(project).computeWithAlternativeResolveEnabled<List<Location<*>>, Throwable> {
                val locationPath = TestLocationPath.parse(path, protocol == JavaTestLocator.TEST_PROTOCOL)
                    ?: return@computeWithAlternativeResolveEnabled emptyList()
                collectLocations(project, scope, locationPath)
            }
        } catch (_: IndexNotReadyException) {
            emptyList()
        }
    }

    override fun getLocation(
        protocol: String,
        path: String,
        project: Project,
        scope: GlobalSearchScope,
    ): List<Location<*>> = getLocation(protocol, path, null, project, scope)

    private fun collectLocations(project: Project, scope: GlobalSearchScope, path: TestLocationPath): List<Location<*>> {
        return KotlinFullClassNameIndex[path.className, project, scope]
            .asSequence()
            .filter { it.fqName?.asString() == path.className }
            .filter { it.module?.isNewMultiPlatformModule == true }
            .mapNotNull { testClass -> createLocation(project, testClass, path.methodName, path.paramName) }
            .toList()
    }

    private fun createLocation(
        project: Project,
        testClass: KtClassOrObject,
        methodName: String?,
        paramName: String?,
    ): Location<*>? {
        val psiClass = testClass.toLightClass() ?: testClass.toFakeLightClass()
        val trimmedMethodName = methodName?.trim()
        if (trimmedMethodName == null || trimmedMethodName == psiClass.name) {
            return KotlinMppGradleClassLocation(project, testClass, psiClass)
        }

        val functions = testClass.body?.declarations
            ?.asSequence()
            ?.filterIsInstance<KtNamedFunction>()
            ?.filter { it.name == trimmedMethodName }
            ?.toList()
            .orEmpty()

        return functions.firstNotNullOfOrNull { function ->
            val method = function.toPsiMethod() ?: return@firstNotNullOfOrNull null
            KotlinMppGradleMethodLocation(project, function, method, psiClass, paramName)
        }
    }

    private fun KtNamedFunction.toPsiMethod(): PsiMethod? {
        return LightClassUtil.getLightClassMethod(this) ?: KtFakeLightMethod.get(this)
    }

    private data class TestLocationPath(
        val className: String,
        val methodName: String?,
        val paramName: String?,
    ) {
        companion object {
            fun parse(sourcePath: String, isTestProtocol: Boolean): TestLocationPath? {
                var path = sourcePath
                val paramName = path.indexOf('[')
                    .takeIf { it >= 0 }
                    ?.let { index ->
                        path.substring(index).also {
                            path = path.substring(0, index)
                        }
                    }

                val slashIndex = path.indexOf('/')
                if (slashIndex > 0) {
                    val className = path.substring(0, slashIndex)
                    val methodName = path.substring(slashIndex + 1).takeIf { it.isNotEmpty() }
                    return TestLocationPath(className, methodName, paramName)
                }

                if (!isTestProtocol) {
                    return TestLocationPath(path.trimEnd('.'), null, paramName)
                }

                val dotIndex = path.lastIndexOf('.')
                if (dotIndex <= 0 || dotIndex == path.lastIndex) {
                    return null
                }
                return TestLocationPath(path.substring(0, dotIndex), path.substring(dotIndex + 1), paramName)
            }
        }
    }

    private class KotlinMppGradleClassLocation(
        project: Project,
        override val sourceElement: KtClassOrObject,
        override val testClass: PsiClass,
    ) : PsiLocation<PsiElement>(project, sourceElement), GradleTestLocationInfo {
        override val testMethod: PsiMethod? = null
        override val testFilter: String = createTestFilterFrom(testClass)

        private val psiClassLocation = PsiLocation(project, testClass)

        override fun <T : PsiElement> getAncestors(ancestorClass: Class<T>, strict: Boolean): Iterator<Location<T>> {
            val locations = mutableListOf<Location<T>>()
            if (!strict) {
                locations.addAncestorIfMatches(ancestorClass, PsiClass::class.java, psiClassLocation)
            }
            return (locations.asSequence() + super.getAncestors(ancestorClass, strict).asSequence()).iterator()
        }
    }

    private class KotlinMppGradleMethodLocation(
        project: Project,
        override val sourceElement: KtNamedFunction,
        override val testMethod: PsiMethod,
        override val testClass: PsiClass,
        paramName: String?,
    ) : PsiMemberParameterizedLocation(project, sourceElement, testClass, paramName), GradleTestLocationInfo {
        override val testFilter: String = createTestFilterFrom(testClass, testMethod.name)

        private val methodLocation = MethodLocation.elementInClass(testMethod, testClass)
        private val containingClassLocation = PsiLocation(project, testClass)

        override fun <T : PsiElement> getAncestors(ancestorClass: Class<T>, strict: Boolean): Iterator<Location<T>> {
            val locations = mutableListOf<Location<T>>()
            if (!strict) {
                locations.addAncestorIfMatches(ancestorClass, PsiMethod::class.java, methodLocation)
            }
            locations.addAncestorIfMatches(ancestorClass, PsiClass::class.java, containingClassLocation)
            return (locations.asSequence() + super.getAncestors(ancestorClass, strict).asSequence()).iterator()
        }
    }
}

private fun <T : PsiElement> MutableList<Location<T>>.addAncestorIfMatches(
    ancestorClass: Class<T>,
    expectedClass: Class<out PsiElement>,
    location: Location<out PsiElement>,
) {
    if (ancestorClass != expectedClass) return
    @Suppress("UNCHECKED_CAST")
    this += location as Location<T>
}
