// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.testGenerator.generator

import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.testGenerator.generator.methods.RunTestMethod
import org.jetbrains.kotlin.testGenerator.generator.methods.TestCaseMethod
import org.jetbrains.kotlin.testGenerator.model.*
import org.junit.runner.RunWith
import java.io.File
import java.util.*
import javax.lang.model.element.Modifier

fun File.toRelativeStringSystemIndependent(base: File): String {
    val path = this.toRelativeString(base)
    return if (File.separatorChar == '\\') {
        path.replace('\\', '/')
    } else path
}

interface TestMethod : RenderElement {
    val methodName: String
}

class SuiteElement private constructor(
    private val group: TGroup, private val suite: TSuite, private val model: TModel,
    private val className: String, private val isNested: Boolean,
    methods: List<TestMethod>, nestedSuites: List<SuiteElement>
) : RenderElement {
    private val methods = methods.sortedBy { it.methodName }
    private val nestedSuites = nestedSuites.sortedBy { it.className }

    companion object {
        fun create(group: TGroup, suite: TSuite, model: TModel, className: String, isNested: Boolean): SuiteElement {
            return collect(group, suite, model, model.depth, className, isNested)
        }

        private fun collect(group: TGroup, suite: TSuite, model: TModel, depth: Int, className: String, isNested: Boolean): SuiteElement {
            val rootFile = File(group.testDataRoot, model.path)

            val methods = mutableListOf<TestMethod>()
            val nestedSuites = mutableListOf<SuiteElement>()

            for (file in rootFile.listFiles().orEmpty()) {
                if (depth > 0 && file.isDirectory && file.name !in model.excludedDirectories) {
                    val nestedClassName = file.toJavaIdentifier().capitalize()
                    val nestedModel = model.copy(
                        path = file.toRelativeStringSystemIndependent(group.testDataRoot),
                        testClassName = nestedClassName,
                    )

                    val nestedElement = collect(group, suite, nestedModel, depth - 1, nestedClassName, isNested = true)
                    if (nestedElement.methods.isNotEmpty() || nestedElement.nestedSuites.isNotEmpty()) {
                        if (model.flatten) {
                            methods += flatten(nestedElement)
                        } else {
                            nestedSuites += nestedElement
                        }
                        continue
                    }
                }

                val match = model.matcher(file.name) ?: continue
                val methodNameBase = getTestMethodNameBase(match.methodName)
                val path = file.toRelativeStringSystemIndependent(group.moduleRoot)
                methods += TestCaseMethod(
                    methodNameBase,
                    if (file.isDirectory) "$path/" else path,
                    file.toRelativeStringSystemIndependent(rootFile),
                    group.isCompilerTestData
                )
            }

            if (methods.isNotEmpty()) {
                if (model.testPerClass) {
                    nestedSuites += methods.map {
                        val nestedClassName = it.methodName.capitalize()
                        listOf(it).wrapToNestedClass(
                            group,
                            suite,
                            model,
                            nestedClassName,
                        )
                    }
                    methods.clear()
                } else {
                    if (nestedSuites.isNotEmpty()) {
                        nestedSuites += methods.wrapToNestedClass(
                            group,
                            suite,
                            model,
                        )

                        methods.clear()
                    } else {
                        methods += RunTestMethod(model)
                    }
                }
            }

            val suiteElement = SuiteElement(group, suite, model, className, isNested, methods, nestedSuites)
            return if (model.bucketSize != null) {
                suiteElement.chunked(model.bucketSize)
            } else {
                suiteElement
            }
        }

        private fun List<TestMethod>.wrapToNestedClass(
            group: TGroup,
            suite: TSuite,
            model: TModel,
            name: String = "Uncategorized",
        ): SuiteElement = SuiteElement(
            group = group,
            suite = suite,
            model = model.copy(testClassName = name),
            className = name,
            isNested = true,
            methods = this.takeIf { methods -> methods.none { it.methodName == "runTest" } }?.plus(RunTestMethod(model)) ?: this,
            nestedSuites = emptyList(),
        )

        private fun SuiteElement.chunked(bucketSize: Int): SuiteElement {
            val size = methods.size - 1
            val newNestedClasses = if (size > bucketSize) {

                methods.asSequence()
                    .filter { it.methodName != "runTest" }
                    .chunked(bucketSize)
                    .mapIndexed { index: Int, testMethods: List<TestMethod> ->
                        testMethods.wrapToNestedClass(
                            group,
                            suite,
                            model,
                            "TestBucket${"%03d".format(index + 1)}",
                        )
                    }
                    .toList()
            } else {
                emptyList()
            }

            if (nestedSuites.isEmpty() && newNestedClasses.isEmpty()) return this

            return SuiteElement(
                group,
                suite,
                model,
                className,
                isNested,
                if (newNestedClasses.isEmpty()) methods else emptyList(),
                nestedSuites.map { it.chunked(bucketSize) } + newNestedClasses,
            )
        }

        private fun flatten(element: SuiteElement): List<TestMethod> {
            val modelFileName = File(element.model.path).name

            val ownMethods = element.methods.filterIsInstance<TestCaseMethod>().map { it.embed(modelFileName) }
            val nestedMethods = element.nestedSuites.flatMap { flatten(it) }
            return ownMethods + nestedMethods
        }

        private fun getTestMethodNameBase(path: String): String {
            return path
                .split(File.pathSeparator)
                .joinToString(File.pathSeparator) { makeJavaIdentifier(it).capitalize() }
        }
    }

    override fun Code.render() {
        val testDataPath = File(group.testDataRoot, model.path).toRelativeStringSystemIndependent(group.moduleRoot)

        appendAnnotation(TAnnotation<RunWith>(JUnit3RunnerWithInners::class.java))
        appendAnnotation(TAnnotation<TestMetadata>(testDataPath))
        suite.annotations.forEach { appendAnnotation(it) }

        val modifiers = EnumSet.of(Modifier.PUBLIC)

        if (isNested) {
            modifiers.add(Modifier.STATIC)
        }

        if (methods.isEmpty()) {
            modifiers.add(Modifier.ABSTRACT)
        }

        appendModifiers(modifiers)
        appendBlock("class $className extends ${suite.abstractTestClass.simpleName}") {
            appendList(methods + nestedSuites, separator = "\n\n")
        }
    }
}