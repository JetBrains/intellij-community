// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.changeSignature

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.lang.LanguageRefactoringSupport
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor
import com.intellij.refactoring.changeSignature.ParameterInfoImpl
import com.intellij.refactoring.util.CanonicalTypes
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.VisibilityUtil
import junit.framework.ComparisonFailure
import org.jetbrains.kotlin.idea.base.util.allScope
import org.jetbrains.kotlin.idea.intentions.AddFullQualifierIntention
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.utils.sure
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File
import com.intellij.codeInsight.TargetElementUtil.ELEMENT_NAME_ACCEPTED
import com.intellij.codeInsight.TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex
import com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts
import org.jetbrains.kotlin.asJava.getRepresentativeLightMethod
import org.jetbrains.kotlin.descriptors.Visibilities.Internal
import org.jetbrains.kotlin.descriptors.Visibilities.Private
import org.jetbrains.kotlin.descriptors.Visibilities.Protected
import org.jetbrains.kotlin.descriptors.Visibilities.Public
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionFqnNameIndex
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.declaredMemberFunctions

@RunWith(JUnit38ClassRunner::class)
abstract class BaseKotlinChangeSignatureTest<C: KotlinModifiableChangeInfo<P>, P: KotlinModifiableParameterInfo, TypeInfo, V, MethodDescriptor: KotlinModifiableMethodDescriptor<P, V>> : KotlinLightCodeInsightFixtureTestCase() {
    companion object {
        protected val EXTENSIONS = arrayOf(".kt", ".java")
    }

    protected abstract fun C.createKotlinStringParameter(name: String = "s", defaultValueForCall: KtExpression? = null): P

    protected abstract fun C.createKotlinIntParameter(
            name: String = "i",
            defaultValueForCall: KtExpression? = null,
            defaultValueAsDefaultParameter: Boolean = false,
    ): P

    protected abstract fun C.createKotlinParameter(
            name: String,
            originalType: String?,
            defaultValueForCall: KtExpression? = null,
            defaultValueAsDefaultParameter: Boolean = false,
            currentType: String? = null
    ): P

    protected abstract fun createParameterTypeInfo(type: String?): TypeInfo

    protected abstract fun MethodDescriptor.createNewIntParameter(
            defaultValueForCall: KtExpression? = null,
            withDefaultValue: Boolean = false,
    ): P

    protected abstract fun createChangeInfo(): C

    protected abstract fun doTestWithDescriptorModification(configureFiles: Boolean = true, modificator: MethodDescriptor.() -> Unit)

    abstract fun doRefactoring(configure: C.() -> Unit = {})

    protected abstract fun findCallers(method: PsiMethod): LinkedHashSet<PsiMethod>

    override val testDataDirectory: File
        get() = IDEA_TEST_DATA_DIR.resolve("refactoring/changeSignature")

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    override fun setUp() {
        super.setUp()
        CodeStyle.getSettings(project).clearCodeStyleSettings()
    }

    override fun tearDown() {
        try {
            files = emptyList()
            psiFiles = PsiFile.EMPTY_ARRAY
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    protected val kotlinDefaultIntValue: KtExpression get() = KtPsiFactory(project).createExpression("42")

    protected lateinit var files: List<String>
    protected lateinit var psiFiles: Array<PsiFile>

    protected fun configureFiles() {
        val fileList = mutableListOf<String>()
        var i = 0
        indexLoop@ while (true) {
            for (extension in EXTENSIONS) {
                val extraFileName = getTestName(false) + "Before" + (if (i > 0) ".$i" else "") + extension
                val extraFile = File(testDataDirectory, extraFileName)
                if (extraFile.exists()) {
                    fileList.add(extraFileName)
                    i++
                    continue@indexLoop
                }
            }
            break
        }

        psiFiles = myFixture.configureByFiles(*fileList.toTypedArray())
        files = fileList
    }

    protected fun findTargetElement(): PsiElement? {
        val provider = LanguageRefactoringSupport.INSTANCE.forContext(file)
        return provider!!.changeSignatureHandler!!.findTargetMember(file, editor)
    }

    protected inline fun <reified T> doTestTargetElement(code: String) {
        myFixture.configureByText("dummy.kt", code)
        val element = findTargetElement()!!
        assertEquals(T::class, element::class)
    }

    protected fun createExpressionWithImports(context: PsiElement, expression: String, imports: List<String>): KtExpression? {
        val fragment = KtPsiFactory(project).createExpressionCodeFragment(expression, context)
        project.executeWriteCommand("add imports and qualifiers") {
            fragment.addImportsFromString(imports.joinToString(separator = KtCodeFragment.IMPORT_SEPARATOR) { "import $it" })
            AddFullQualifierIntention.Holder.addQualifiersRecursively(fragment) //todo
        }

        return fragment.getContentElement()
    }

    private fun MethodDescriptor.addNewIntParameterWithValue(asParameter: Boolean, index: Int? = null) {
        val newIntParameter = createNewIntParameter(defaultValueForCall = kotlinDefaultIntValue, withDefaultValue = asParameter)
        if (index != null) {
            addParameter(index, newIntParameter)
        } else {
            addParameter(newIntParameter)
        }
    }


    protected class JavaRefactoringConfiguration(val method: PsiMethod) {
        val project = method.project

        var newName = method.name
        var newReturnType: PsiType = method.returnType ?: PsiTypes.voidType()
        val newParameters = ArrayList<ParameterInfoImpl>()
        val parameterPropagationTargets = LinkedHashSet<PsiMethod>()

        val psiFactory: PsiElementFactory
            get() = JavaPsiFacade.getInstance(project).elementFactory

        val objectPsiType: PsiType
            get() = PsiType.getJavaLangObject(method.manager, project.allScope())

        val stringPsiType: PsiType
            get() = PsiType.getJavaLangString(method.manager, project.allScope())

        init {
            method.parameterList.parameters
                    .withIndex()
                    .mapTo(newParameters) {
                        val (i, param) = it
                        ParameterInfoImpl(i, param.name, param.type)
                    }
        }

        fun createProcessor(): ChangeSignatureProcessor = ChangeSignatureProcessor(
                project,
                method,
                false,
                VisibilityUtil.getVisibilityModifier(method.modifierList),
                newName,
                CanonicalTypes.createTypeWrapper(newReturnType),
                newParameters.toTypedArray(),
                arrayOf(),
                parameterPropagationTargets,
                emptySet<PsiMethod>()
        )
    }

    protected fun doTest(configure: C.() -> Unit = {}) {
        configureFiles()
        doRefactoring(configure)
        compareEditorsWithExpectedData()
    }

    protected fun compareEditorsWithExpectedData() {
        //noinspection ConstantConditions
        val checkErrorsAfter = InTextDirectivesUtils.isDirectiveDefined(file!!.text, "// CHECK_ERRORS_AFTER")
        for ((file, psiFile) in files zip psiFiles) {
            val afterFilePath = file.replace("Before.", "After.")
            try {
                myFixture.checkResultByFile(file, afterFilePath, true)
            } catch (e: ComparisonFailure) {
                KotlinTestUtils.assertEqualsToFile(File(testDataDirectory, afterFilePath), psiFile.text)
            }

            if (checkErrorsAfter && psiFile is KtFile) {
                DirectiveBasedActionUtils.checkForUnexpectedErrors(psiFile)
            }
        }
    }

    protected fun doTestAndIgnoreConflicts(configure: C.() -> Unit = {}) {
        withIgnoredConflicts<Throwable> {
            doTest(configure)
        }
    }

    private fun C.swapParameters(i: Int, j: Int) {
        val newParameters = newParameters
        val temp = newParameters[i]
        setNewParameter(i, newParameters[j])
        setNewParameter(j, temp)
    }

    protected fun doTestConflict(configure: C.() -> Unit = {}) = runAndCheckConflicts { doTest(configure) }

    protected fun doTestUnmodifiable(configure: C.() -> Unit = {}) {
        try {
            doTest(configure)
            fail("No conflicts found")
        } catch (e: RuntimeException) {
            if ((e.message ?: "").contains("Cannot modify file")) return

            val message = when {
                e is BaseRefactoringProcessor.ConflictsInTestsException -> StringUtil.join(e.messages.sorted(), "\n")
                e is CommonRefactoringUtil.RefactoringErrorHintException -> e.message
                e.message!!.startsWith("Refactoring cannot be performed") -> e.message
                else -> throw e
            }
            val conflictsFile = File(testDataDirectory, getTestName(false) + "Messages.txt")
            assertSameLinesWithFile(conflictsFile.absolutePath, message!!)
        }
    }


    protected fun doJavaTest(configure: JavaRefactoringConfiguration.() -> Unit) {
        configureFiles()

        val targetElement = TargetElementUtil.findTargetElement(editor, ELEMENT_NAME_ACCEPTED or REFERENCED_ELEMENT_ACCEPTED)
        val targetMethod = (targetElement as? PsiMethod).sure { "<caret> is not on method name" }

        JavaRefactoringConfiguration(targetMethod).apply { configure() }.createProcessor().run()

        compareEditorsWithExpectedData()
    }

    protected fun doJavaTestConflict(configure: JavaRefactoringConfiguration.() -> Unit) = runAndCheckConflicts { doJavaTest(configure) }


    protected fun runAndCheckConflicts(testAction: () -> Unit) {
        try {
            testAction()
            fail("No conflicts found")
        } catch (e: Throwable) {
            val message = when {
                e is BaseRefactoringProcessor.ConflictsInTestsException -> StringUtil.join(e.messages.sorted(), "\n")
                e is CommonRefactoringUtil.RefactoringErrorHintException -> e.message
                e is RuntimeException && e.message!!.startsWith("Refactoring cannot be performed") -> e.message
                else -> throw e
            }
            val conflictsFile = File(testDataDirectory, getTestName(false) + "Messages.txt")
            assertSameLinesWithFile(conflictsFile.absolutePath, message!!)
        }
    }

    // --------------------------------- Tests ---------------------------------

    fun testAllTestsPresented() {

        val functionNames = this::class.allSuperclasses.flatMap {
            it.declaredMemberFunctions
                    .asSequence()
                    .map { it.name }
                    .filter { it.startsWith("test") }
                    .map { it.removePrefix("test") }
                    .map { it + "Before" }
        }
                .toSet()

        for (file in testDataDirectory.listFiles()!!) {
            val fileName = file.name.substringBefore(".")
            if (fileName.endsWith("Messages") || fileName.endsWith("After")) continue

            assertTrue(
              "test function for ${file.name} not found",
              fileName in functionNames,
            )
        }
    }

    fun testPreferContainedInClass() {
        configureFiles()
        assertEquals("param", createChangeInfo().newParameters[0].name)
    }

    fun testBadSelection() {
        myFixture.configureByFile(getTestName(false) + "Before.kt")
        assertNull(findTargetElement())
    }

    fun testPositionOnNameForPropertyInsideConstructor() = doTestTargetElement<KtParameter>("class A(val <caret>a: String)")
    fun testPositionOnValForPropertyInsideConstructor() = doTestTargetElement<KtParameter>("class A(v<caret>al a: String)")
    fun testPositionOnVarForPropertyInsideConstructor() = doTestTargetElement<KtParameter>("class A(v<caret>ar a: String)")
    fun testPositionBeforeColonForPropertyInsideConstructor() = doTestTargetElement<KtParameter>("class A(var a<caret>: String)")
    fun testPositionAfterColonForPropertyInsideConstructor() = doTestTargetElement<KtParameter>("class A(var a:<caret> String)")

    fun testPositionOnTypeForPropertyInsideConstructor() = doTestTargetElement<KtPrimaryConstructor>("class A(var a: St<caret>ring)")
    fun testPositionParameterInsideConstructor() = doTestTargetElement<KtPrimaryConstructor>("class A(<caret>a: String)")

    fun testPositionOnVarForProperty() = doTestTargetElement<KtProperty>("v<caret>ar a: String = \"\"")
    fun testPositionOnValForProperty() = doTestTargetElement<KtProperty>("v<caret>al a: String = \"\"")
    fun testPositionOnNameForProperty() = doTestTargetElement<KtProperty>("val <caret>a: String = \"\"")

    fun testPositionOnInvokeArgument() = doTestTargetElement<KtCallExpression>(
      """
            class WithInvoke
            operator fun WithInvoke.invoke() {}
            fun checkInvoke(w: WithInvoke) = w(<caret>)
    """.trimIndent()
    )

    fun testPositionOnInvokeObject() = doTestTargetElement<KtCallExpression>(
      """
            object InvokeObject {
                operator fun invoke() {}
            } 
        
            val invokeObjectCall = InvokeObject(<caret>)
    """.trimIndent()
    )

    fun testCaretAtReferenceAsValueParameter() = doTestConflict()

    fun testSynthesized() = doTestConflict()

    fun testRemoveParameterFromFunctionWithReceiver() = doJavaTest { newParameters.clear() }

    fun testAddParameterFromFunctionWithReceiver() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "i", PsiTypes.intType())) }

    fun testChangeJavaMethod() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "i", PsiTypes.intType())) }

    fun testChangeJavaMethodWithPrimitiveType() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "s", stringPsiType)) }

    fun testChangeJavaMethodWithBoxedType() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "s", stringPsiType)) }

    fun testChangeJavaMethodWithFlexibleMutableType() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "s", stringPsiType)) }

    fun testChangeJavaMethodWithNestedFlexibleType() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "i", PsiTypes.intType())) }

    fun testChangeJavaMethodWithFlexibleMutableType1() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "s", stringPsiType)) }

    fun testChangeJavaMethodWithRawType() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "i", PsiTypes.intType())) }

    fun testSimpleFlexibleType() = doJavaTest { newParameters.add(javaIntegerParameter()) }

    fun testSimpleFlexibleType2() = doJavaTest { newParameters.add(javaIntegerParameter()) }

    fun testMutableFlexibleType() = doJavaTest { newParameters.add(javaIntegerParameter()) }

    fun testMutableFlexibleType2() = doJavaTest { newParameters.add(javaIntegerParameter()) }

    fun testMutableFlexibleType3() = doJavaTest { newParameters.add(javaIntegerParameter()) }

    fun testMutableFlexibleType4() = doJavaTest { newParameters.add(javaIntegerParameter()) }

    fun testMutableFlexibleType5() = doJavaTest { newParameters.add(javaIntegerParameter()) }

    fun testMutableFlexibleType6() = doJavaTest { newParameters.add(javaIntegerParameter()) }

    fun testMutableFlexibleType7() = doJavaTest { newParameters.add(javaIntegerParameter()) }

    fun testMutableFlexibleType8() = doJavaTest { newParameters.add(javaIntegerParameter()) }

    private fun javaIntegerParameter() = ParameterInfoImpl(-1, "integer", PsiTypes.intType())

    fun testJavaMethodKotlinUsages() = doJavaTest {
        newName = "bar"
        newParameters.removeAt(1)
    }

    fun testJavaMethodJvmStaticKotlinUsages() = doJavaTest {
        val first = newParameters[1]
        newParameters[1] = newParameters[0]
        newParameters[0] = first
    }

    fun testJavaConstructorKotlinUsages() = doJavaTest { newParameters.removeAt(1) }

    fun testSAMAddToEmptyParamList() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "s", stringPsiType)) }

    fun testSAMAddToSingletonParamList() = doJavaTest { newParameters.add(0, ParameterInfoImpl(-1, "n", PsiTypes.intType())) }

    fun testSAMAddToNonEmptyParamList() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "o", objectPsiType)) }

    fun testSAMRemoveSingletonParamList() = doJavaTest { newParameters.clear() }

    fun testSAMRemoveParam() = doJavaTest { newParameters.removeAt(0) }

    fun testSAMRenameParam() = doJavaTest { newParameters[0].name = "p" }

    fun testSAMChangeParamType() = doJavaTest { newParameters[0].setType(objectPsiType) }

    fun testSAMRenameMethod() = doJavaTest { newName = "bar" }

    fun testSAMChangeMethodReturnType() = doJavaTest { newReturnType = objectPsiType }

    fun testGenericsWithSAMConstructors() = doJavaTest {
        newParameters[0].setType(psiFactory.createTypeFromText("java.util.List<X<B>>", method.parameterList))
        newParameters[1].setType(psiFactory.createTypeFromText("X<java.util.Set<A>>", method.parameterList))

        newReturnType = psiFactory.createTypeFromText("X<java.util.List<A>>", method)
    }

    fun testJavaConstructorInDelegationCall() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "s", stringPsiType, "\"foo\"")) }


    fun testJavaConstructorBySuperRef() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "s", stringPsiType, "\"foo\"")) }

    fun testJavaMethodOverridesReplaceParam() = doJavaTestConflict {
        newReturnType = stringPsiType
        newParameters[0] = ParameterInfoImpl(-1, "x", PsiTypes.intType(), "1")
    }

    fun testJavaMethodOverridesChangeParam() = doJavaTest {
        newReturnType = stringPsiType

        newParameters[0].name = "x"
        newParameters[0].setType(PsiTypes.intType())
    }

    fun testJavaParameterPropagation() = doJavaTest {
        newParameters.add(ParameterInfoImpl(-1, "n", PsiTypes.intType(), "1"))
        newParameters.add(ParameterInfoImpl(-1, "s", stringPsiType, "\"abc\""))

        val classA = JavaFullClassNameIndex.getInstance().getClasses("A", project, project.allScope()).first { it.name == "A" }
        val methodBar = classA.methods.first { it.name == "bar" }
        parameterPropagationTargets.add(methodBar)

        val functionTest = KotlinTopLevelFunctionFqnNameIndex.get("test", project, project.allScope()).first()
        parameterPropagationTargets.add(functionTest.getRepresentativeLightMethod()!!)
    }


    fun testJavaConstructorParameterPropagation() = doJavaTest {
        newParameters.add(ParameterInfoImpl(-1, "n", PsiTypes.intType(), "1"))
        parameterPropagationTargets.addAll(findCallers(method))
    }

    fun testJavaMethodOverridesOmitUnitType() = doJavaTest {}

    fun testKotlinOverridingJavaWithDifferentParamName() = doJavaTest {
        newParameters.add(ParameterInfoImpl(-1, "n", PsiTypes.intType()))
    }

    fun testAddNewParameterWithDefaultValueToFunctionWithEmptyArguments() = doTestWithDescriptorModification {
        addNewIntParameterWithValue(true)
    }

    fun testAddNewLastParameterWithDefaultValue() = doTestWithDescriptorModification {
        addNewIntParameterWithValue(true)
    }

    fun testAddNewLastParameterWithDefaultValue2() = doTestWithDescriptorModification {
        addNewIntParameterWithValue(true)
    }

    fun testAddNewLastParameterWithDefaultValue3() = doTestWithDescriptorModification {
        addNewIntParameterWithValue(true)
    }

    fun testAddNewFirstParameterWithDefaultValue() = doTestWithDescriptorModification {
        addNewIntParameterWithValue(true, 0)
    }

    fun testAddNewMiddleParameterWithDefaultValue() = doTestWithDescriptorModification {
        addNewIntParameterWithValue(true, 2)
    }

    fun testAddNewParameterToFunctionWithEmptyArguments() = doTestWithDescriptorModification {
        addNewIntParameterWithValue(false)
    }

    fun testAddNewLastParameter() = doTestWithDescriptorModification {
        addNewIntParameterWithValue(false)
    }

    fun testAddNewLastParameter2() = doTestWithDescriptorModification {
        addNewIntParameterWithValue(false)
    }

    fun testAddNewLastParameter3() = doTestWithDescriptorModification {
        addNewIntParameterWithValue(false)
    }

    fun testAddNewFirstParameter() = doTestWithDescriptorModification {
        addNewIntParameterWithValue(false, 0)
    }

    fun testAddNewMiddleParameter() = doTestWithDescriptorModification {
        addNewIntParameterWithValue(false, 2)
    }

    fun testAddNewMiddleParameterKotlinWithoutMixedArgument() {
        configureFiles()
        withCustomCompilerOptions("// COMPILER_ARGUMENTS: -XXLanguage:-MixedNamedArgumentsInTheirOwnPosition", project, module) {
            doTestWithDescriptorModification(configureFiles = false) {
                addNewIntParameterWithValue(false, 2)
            }
        }
    }

    fun testAddParameterToInvokeFunction() = doTestWithDescriptorModification {
        addNewIntParameterWithValue(false)
    }

    fun testAddParameterToInvokeFunctionFromObject() = doTestWithDescriptorModification {
        addNewIntParameterWithValue(false)
    }

    fun testAddFunctionParameterWithDefaultValue() = doTestWithDescriptorModification {
        val expression = createExpressionWithImports(
          method,
          "Dep.MY_CONSTANT_FROM_DEP",
          listOf("a.b.c.Dep"),
        )

        addParameter(createNewIntParameter(defaultValueForCall = expression))
    }

    fun testAddFunctionParameterWithDefaultValue2() = doTestWithDescriptorModification {
        val expression = createExpressionWithImports(method, "MY_CONSTANT_FROM_DEP", listOf("a.b.c.Dep.Companion.MY_CONSTANT_FROM_DEP"))
        addParameter(createNewIntParameter(defaultValueForCall = expression))
    }

    fun testAddFunctionReceiverWithDefaultValue() = doTestWithDescriptorModification {
        val expression = createExpressionWithImports(method, "Dep.MY_CONSTANT_FROM_DEP", listOf("a.b.c.Dep"))
        receiver = createNewIntParameter(defaultValueForCall = expression)
    }

    fun testAddPropertyReceiverWithDefaultValue() = doTestWithDescriptorModification {
        val expression = createExpressionWithImports(method, "Dep.MY_CONSTANT_FROM_DEP", listOf("a.b.c.Dep"))
        receiver = createNewIntParameter(defaultValueForCall = expression)
    }


    fun testAddPropertyReceiverWithDefaultValue2() = doTestWithDescriptorModification {
        val expression = createExpressionWithImports(
          method,
          "MY_CONSTANT_FROM_DEP",
          listOf("a.b.c.Dep.Companion.MY_CONSTANT_FROM_DEP"),
        )

        receiver = createNewIntParameter(defaultValueForCall = expression)
    }



    fun testAddPropertyReceiverWithComplexDefaultValue() = doTestWithDescriptorModification {
        val expression = createExpressionWithImports(
          context = method,
          expression = "Dep2().eval(MY_CONSTANT_FROM_DEP + NUMBER)",
          imports = listOf("a.b.c.Dep.Companion.MY_CONSTANT_FROM_DEP", "a.b.Dep2.Companion.NUMBER", "a.b.Dep2"),
        )

        receiver = createNewIntParameter(defaultValueForCall = expression)
    }

    fun testUnmodifiableFromLibrary() = doTestUnmodifiable()

    fun testUnmodifiableFromBuiltins() = doTestUnmodifiable()


    fun testFunctionFromStdlibConflict() = doTestUnmodifiable()

    fun testExtensionFromStdlibConflict() = doTestUnmodifiable()


    fun testAddNewLastParameterWithDefaultValueToJava() = doTestConflict {
        addParameter(createKotlinIntParameter(defaultValueAsDefaultParameter = true))
    }

    fun testAddParameterToAnnotation() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("3")
        addParameter(createKotlinIntParameter(name = "p3", defaultValueForCall = defaultValueForCall))
    }

    fun testInvokeOperatorInClass() = doTest {
        addParameter(createKotlinIntParameter(defaultValueForCall = kotlinDefaultIntValue))
    }

    fun testInvokeOperatorInObject() = doTest {
        addParameter(createKotlinIntParameter(defaultValueForCall = kotlinDefaultIntValue))
    }

    fun testRemoveUsedReceiver() = doTestConflict {
        removeParameter(0)
    }

    fun testRemoveLastNonLambdaParameter() = doTest { removeParameter(0) }
    fun testRemoveLastNonLambdaParameter2() = doTest { removeParameter(1) }


    fun testRemoveUsedReceiver2() = doTestConflict {
        removeParameter(0)
    }

    fun testRemoveUsedInParametersReceiver() = doTestConflict {
        removeParameter(0)
    }

    fun testRemoveUsedInParametersReceiver2() = doTestConflict {
        removeParameter(0)
    }

    fun testRemoveParameterInParentConflict() = doTestConflict {
        removeParameter(0)
    }

    fun testRemoveParameterInParentConflict2() = doTestConflict {
        removeParameter(0)
    }

    fun testRemoveParameterInParentConflict3() = doTestConflict {
        removeParameter(0)
    }

    fun testRemoveParameterInParentConflict4() = doTestConflict { removeParameter(0) }

    fun testRemoveParameterInParentConflict5() = doTestConflict { removeParameter(1) }

    fun testRemoveUsedReceiverExplicitThis() = doTestConflict {
        removeParameter(0)
    }


    fun testFakeOverride() = doTest {
        addParameter(createKotlinIntParameter())
    }


    fun testVarargs() = doTestConflict()


    fun testInnerFunctionsConflict() = doTestConflict {
        newName = "inner2"
        newParameters[0].name = "y"
    }

    fun testParameterConflict() = doTestConflict {
        clearParameters()
    }

    fun testParameterConflict2() = doTestConflict {
        clearParameters()
    }

    fun testMemberFunctionsConflict() = doTestConflict {
        newName = "inner2"
        newParameters[0].name = "y"
    }

    fun testTopLevelFunctionsConflict() = doTestConflict { newName = "fun2" }

    fun testConstructorsConflict() = doTestConflict {
        newParameters[0].name = "_x"
        newParameters[1].name = "_y"
        newParameters[2].name = "_z"
    }

    fun testOverridesInEnumEntries() = doTest {
        addParameter(createKotlinStringParameter())
    }

    fun testEnumEntriesWithoutSuperCalls() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("1")
        addParameter(createKotlinIntParameter(name = "n", defaultValueForCall = defaultValueForCall))
    }

    fun testConstructorJavaUsages() = doTestAndIgnoreConflicts {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"abc\"")
        addParameter(createKotlinStringParameter(defaultValueForCall = defaultValueForCall))
    }

    fun testFunctionRenameJavaUsages() = doTest { newName = "bar" }

    fun testParameterModifiers() = doTest {
        addParameter(createKotlinIntParameter(name = "n"))
    }

    fun testObjectMember() = doTest { removeParameter(0) }



    fun testParameterListRemoveParam() = doTest { removeParameter(getNewParameters().size - 1) }

    fun testParameterListRemoveAllParams() = doTest { clearParameters() }


    fun testRemoveReceiver() = doTest { removeParameter(0) }

    fun testRemoveReceiverConflict() = doTestConflict { removeParameter(0) }

    fun testRemoveReceiverInParentConflict() = doTestConflict { removeParameter(0) }

    fun testRemoveReceiverInParentConflict2() = doTestConflict { removeParameter(0) }

    fun testRemoveReceiverInParentConflict3() = doTestConflict { removeParameter(0) }

    fun testRemoveReceiverInParentConflict4() = doTestConflict { removeParameter(0) }

    fun testRemoveReceiverForMember() = doTest { removeParameter(0) }

    fun testRemoveReceiverForMemberConflict() = doTestConflict { removeParameter(0) }

    fun testSecondaryConstructor() = doTestAndIgnoreConflicts {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"foo\"")
        addParameter(createKotlinStringParameter(defaultValueForCall = defaultValueForCall))
    }

    fun testPrimaryConstructorOnConstructorKeyword() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"foo\"")
        addParameter(createKotlinStringParameter(defaultValueForCall = defaultValueForCall))
    }

    fun testPrimaryConstructorByThisRef() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"foo\"")
        addParameter(createKotlinStringParameter(defaultValueForCall = defaultValueForCall))
    }

    fun testPrimaryConstructorBySuperRef() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"foo\"")
        addParameter(createKotlinStringParameter(defaultValueForCall = defaultValueForCall))
    }

    fun testSecondaryConstructorByThisRef() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"foo\"")
        addParameter(createKotlinStringParameter(defaultValueForCall = defaultValueForCall))
    }

    fun testSecondaryConstructorBySuperRef() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"foo\"")
        addParameter(createKotlinStringParameter(defaultValueForCall = defaultValueForCall))
    }


    fun testNoConflictWithReceiverName() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("0")
        addParameter(createKotlinIntParameter(defaultValueForCall = defaultValueForCall))
    }

    fun testRemoveParameterBeforeLambda() = doTest { removeParameter(1) }


    fun testRemoveLambdaParameter() = doTest { removeParameter(2) }

    fun testRemoveLambdaParameterConflict() = doTestConflict { removeParameter(2) }

    fun testRemoveEnumConstructorParameter() = doTest { removeParameter(1) }

    fun testRemoveAllEnumConstructorParameters() = doTest { clearParameters() }


    fun testRenameExtensionParameter() = doTest { newParameters[1].name = "b" }

    fun testPrimaryConstructorByRef() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("1")
        addParameter(createKotlinIntParameter(name = "n", defaultValueForCall = defaultValueForCall))
    }

    fun testChangeClassParameterWithInvalidCharacter() = doTest {
        newParameters[0].name = "a@bc"
    }

    fun testChangeClassParameterWithEscapedName() = doTest {
        newParameters[0].name = "`x@yz`"
    }

    fun testNamedLambdaArgumentIsNotMovedOutsideParentheses() = doTest {
        newParameters[0].name = "p11"
    }

    fun testMultipleLambdaArgumentsAreNotMovedOutsideParentheses() = doTest {
        newParameters[0].name = "p11"
    }
    private fun doTestJvmOverloadedAddDefault(index: Int) = doTestAndIgnoreConflicts {
        val defaultValue = KtPsiFactory(project).createExpression("2")
        addParameter(
          createKotlinIntParameter(name = "n", defaultValueForCall = defaultValue, defaultValueAsDefaultParameter = true),
          index,
        )
    }

    private fun doTestJvmOverloadedAddNonDefault(index: Int) = doTestAndIgnoreConflicts {
        val defaultValue = KtPsiFactory(project).createExpression("2")
        addParameter(
          createKotlinIntParameter(name = "n", defaultValueForCall = defaultValue),
          index,
        )
    }

    fun testJvmOverloadedAddDefault1() = doTestJvmOverloadedAddDefault(0)

    fun testJvmOverloadedAddDefault2() = doTestJvmOverloadedAddDefault(1)

    fun testJvmOverloadedAddDefault3() = doTestJvmOverloadedAddDefault(-1)

    fun testJvmOverloadedAddNonDefault1() = doTestJvmOverloadedAddNonDefault(0)

    fun testJvmOverloadedAddNonDefault2() = doTestJvmOverloadedAddNonDefault(1)

    fun testJvmOverloadedAddNonDefault3() = doTestJvmOverloadedAddNonDefault(-1)

    fun testJvmOverloadedRemoveDefault1() = doTestAndIgnoreConflicts { removeParameter(0) }

    fun testJvmOverloadedRemoveDefault2() = doTestAndIgnoreConflicts { removeParameter(1) }

    fun testJvmOverloadedRemoveDefault3() = doTestAndIgnoreConflicts { removeParameter(getNewParameters().size - 1) }

    fun testJvmOverloadedRemoveNonDefault1() = doTestAndIgnoreConflicts { removeParameter(0) }

    fun testJvmOverloadedRemoveNonDefault2() = doTestAndIgnoreConflicts { removeParameter(1) }

    fun testJvmOverloadedRemoveNonDefault3() = doTestAndIgnoreConflicts { removeParameter(getNewParameters().size - 1) }

    fun testRemoveDataClassParameter() = doTest { removeParameter(1) }


    fun testRemoveParameterKeepFormat1() = doTest { removeParameter(0) }

    fun testRemoveParameterKeepFormat2() = doTest { removeParameter(1) }

    fun testRemoveParameterKeepFormat3() = doTest { removeParameter(2) }

    fun testGetConventionRenameToFoo() = doTest { newName = "foo" }

    fun testGetConventionRenameToInvoke() = doTest { newName = "invoke" }

    fun testAddParameterAfterLambdaParameter() = doTest {
        addParameter(createKotlinIntParameter(defaultValueForCall = KtPsiFactory(project).createExpression("0")))
    }

    fun testRemoveLambdaParameter2() = doTest { removeParameter(0) }


    fun testInvokeConventionRenameToFoo() = doTest { newName = "foo" }

    fun testInvokeConventionRenameToGet() = doTest { newName = "get" }

    fun testGetConventionRemoveParameter() = doTest { removeParameter(0) }

    fun testRenameExtensionParameterWithNamedArgs() = doTest { newParameters[2].name = "bb" }

    fun testOverrideInAnonymousObjectWithTypeParameters() = doTest { newName = "bar" }

    fun testJvmOverloadedRenameParameter() = doTest { newParameters[0].name = "aa" }

    fun testRemoveDefaultParameterBeforeLambda() = doTest { removeParameter(1) }

    fun testConstructorSwapArguments() = doTest {
        newParameters[0].name = "_x1"
        newParameters[1].name = "_x2"

        swapParameters(0, 2)
    }

    fun testNoDefaultValuesInOverrides() = doTest { swapParameters(0, 1) }

    fun testJvmOverloadedSwapParams1() = doTestAndIgnoreConflicts { swapParameters(1, 2) }

    fun testJvmOverloadedSwapParams2() = doTestAndIgnoreConflicts { swapParameters(0, 2) }



    fun testJvmOverloadedConstructorSwapParams() = doTestAndIgnoreConflicts { swapParameters(1, 2) }

    fun testDefaultAfterLambda() = doTest { swapParameters(0, 1) }

    fun testSwapDataClassParameters() = doTest {
        swapParameters(0, 2)
        swapParameters(1, 2)
    }


    fun testSwapParametersKeepFormat() = doTest { swapParameters(0, 2) }

    fun testGetConventionSwapParameters() = doTest { swapParameters(0, 1) }



    fun testMoveLambdaParameterToLast() = doTest { swapParameters(0, 1) }

    fun testInvokeConventionSwapParameters() = doTest { swapParameters(0, 1) }


    fun testRemoveParameterKeepOtherComments() = doTest { removeParameter(1) }

    fun testAddParameterKeepFormat() = doTest {
        val psiFactory = KtPsiFactory(project)
        val defaultValue1 = psiFactory.createExpression("4")
        val defaultValue2 = psiFactory.createExpression("5")
        addParameter(createKotlinIntParameter("d", defaultValue1), 2)
        addParameter(
          createKotlinIntParameter("e", defaultValue2)
        )
    }

    fun testMoveLambdaParameter() = doTest {
        val newParameters = newParameters
        setNewParameter(1, newParameters[2])
        setNewParameter(2, newParameters[1])
    }

    fun testExpressionFunction() = doTest {
        newParameters[0].name = "x1"

        addParameter(createKotlinIntParameter("y1"))
    }

    fun testAddDataClassParameter() = doTest {
        addParameter(
          createKotlinIntParameter("c", KtPsiFactory(project).createExpression("3")).apply { valOrVar = KotlinValVar.Val },
          1,
        )
    }

    fun testRemoveAllOriginalDataClassParameters() = doTest {
        val psiFactory = KtPsiFactory(project)

        swapParameters(1, 2)
        setNewParameter(
          0,
          createKotlinIntParameter("d", psiFactory.createExpression("4")).apply { valOrVar = KotlinValVar.Val }
        )

        setNewParameter(
          2,
          createKotlinIntParameter("e", psiFactory.createExpression("5")).apply { valOrVar = KotlinValVar.Val }
        )
    }

    fun testNewParamValueRefsOtherParam() = doTest {
        val codeFragment = KtPsiFactory(project).createExpressionCodeFragment("p1 * p1", context)
        addParameter(createKotlinIntParameter("p2", codeFragment.getContentElement()!!))
    }


    fun testParameterChangeInOverrides() = doTest {
        newParameters[0].name = "n"
        newParameters[0].setType("Int")
    }


    fun testGetConventionParameterToReceiver() = doTestConflict { receiverParameterInfo = newParameters[0] }

    fun testGetConventionReceiverToParameter() = doTest { receiverParameterInfo = null }


    fun testInvokeConventionParameterToReceiver() = doTestConflict { receiverParameterInfo = newParameters[0] }

    fun testInvokeConventionReceiverToParameter() = doTest { receiverParameterInfo = null }

    fun testInvokeConventionRemoveParameter() = doTest { removeParameter(0) }

    fun testImplicitReceiverInRecursiveCall() = doTest {
        receiverParameterInfo = null
        newParameters[0].name = "a"
    }

    fun testReceiverInSafeCall() = doTestConflict { receiverParameterInfo = null }


    fun testMakePrimaryConstructorPrivateNoParams() = doTest { setNewVisibility(Private) }

    fun testMakePrimaryConstructorPublic() = doTest { setNewVisibility(Public) }


    fun testRemoveTopLevelPropertyReceiver() = doTest { receiverParameterInfo = null }

    fun testRemovePropertyReceiver() = doTest { receiverParameterInfo = null }


    fun testConvertToExtensionAndRename() = doTest {
        receiverParameterInfo = newParameters[0]
        newName = "foo1"
    }

    fun testConvertParameterToReceiverAddParents() = doTest { receiverParameterInfo = newParameters[0] }

    fun testThisReplacement() = doTest { receiverParameterInfo = null }


    fun testReceiverToParameterExplicitReceiver() = doTest { receiverParameterInfo = null }

    fun testReceiverToParameterImplicitReceivers() = doTest { receiverParameterInfo = null }

    fun testParameterToReceiverExplicitReceiver() = doTest { receiverParameterInfo = newParameters[0] }

    fun testParameterToReceiverImplicitReceivers() = doTest { receiverParameterInfo = newParameters[0] }

    fun testConvertLambdaParameterToReceiver() = doTest { receiverParameterInfo = newParameters[2] }

    fun testDoNotApplyPrimarySignatureToSecondaryCalls() = doTest {
        val newParameters = newParameters
        setNewParameter(0, newParameters[1])
        setNewParameter(1, newParameters[0])
    }

    fun testConvertReceiverToParameterForMember2() = doTest {
        receiverParameterInfo = null

        val newParameters = newParameters
        setNewParameter(0, newParameters[1])
        setNewParameter(1, newParameters[0])
    }

    fun testConvertReceiverToParameterWithNameClash() = doTest { receiverParameterInfo = null }

    fun testConvertReceiverToParameterAndChangeName() = doTest {
        receiverParameterInfo = null
        newParameters[0].name = "abc"
    }

    fun testChangeReceiver() = doTest { receiverParameterInfo = newParameters[1] }

    fun testChangeReceiverForMember() = doTest { receiverParameterInfo = newParameters[1] }


    fun testConvertParameterToReceiverForMember1() = doTest { receiverParameterInfo = newParameters[0] }

    fun testConvertParameterToReceiverForMemberUltraLight() = doTest { receiverParameterInfo = newParameters[0] }

    fun testConvertParameterToReceiverForMember2() = doTest { receiverParameterInfo = newParameters[1] }

    fun testConvertParameterToReceiverForMemberConflict() = doTestConflict { receiverParameterInfo = newParameters[0] }

    fun testConvertReceiverToParameterForMember1() = doTest { receiverParameterInfo = null }


    fun testAddNewReceiver() = doTestAndIgnoreConflicts {
        val defaultValueForCall = KtPsiFactory(project).createExpression("X(0)")
        receiverParameterInfo = createKotlinParameter("_", "Any", defaultValueForCall, currentType = "X")
    }

    fun testAddNewReceiverForMember() = doTestAndIgnoreConflicts {
        val defaultValueForCall = KtPsiFactory(project).createExpression("X(0)")
        receiverParameterInfo = createKotlinParameter("_", "Any", defaultValueForCall, currentType = "X")
    }

    fun testAddNewReceiverForMemberConflict() = doTestConflict {
        val defaultValueForCall = KtPsiFactory(project).createExpression("X(0)")
        receiverParameterInfo = createKotlinParameter("_", "Any", defaultValueForCall, currentType = "X")
    }

    fun testAddNewReceiverConflict() = doTestConflict {
        val defaultValueForCall = KtPsiFactory(project).createExpression("X(0)")
        receiverParameterInfo = createKotlinParameter("_", "Any", defaultValueForCall, currentType = "X")
    }

    fun testConvertParameterToReceiver1() = doTest { receiverParameterInfo = newParameters[0] }

    fun testConvertParameterToReceiver2() = doTest { receiverParameterInfo = newParameters[1] }

    fun testConvertReceiverToParameter1() = doTest { receiverParameterInfo = null }

    fun testConvertReceiverToParameter2() = doTest {
        receiverParameterInfo = null

        val newParameters = newParameters
        setNewParameter(0, newParameters[1])
        setNewParameter(1, newParameters[0])
    }


    fun testFqNameShortening() = doTest {
        addParameter(createKotlinParameter("s", originalType = "Any", currentType = "kotlin.String"))
    }

    fun testChangeConstructorVisibility() = doTest { setNewVisibility(Protected) }


    fun testRenameFunction() = doTest { newName = "after" }


    fun testFunctionsAddRemoveArgumentsConflict() = doTestConflict {
        setNewVisibility(Internal)

        val defaultValueForCall = KtPsiFactory(project).createExpression("null")
        val newParameters = newParameters
        setNewParameter(2, newParameters[1])
        setNewParameter(1, newParameters[0])
        setNewParameter(
          index = 0,
          parameterInfo = createKotlinParameter("x0", "Any?", defaultValueForCall = defaultValueForCall)
        )
    }

    fun testAddTopLevelPropertyReceiver() = doTestAndIgnoreConflicts {
        val defaultValueForCall = KtPsiFactory(project).createExpression("A()")
        receiverParameterInfo = createKotlinParameter("receiver", null, defaultValueForCall, currentType = "test.A")
    }


    fun testAddPropertyReceiverConflict() = doTestConflict {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"\"")
        receiverParameterInfo = createKotlinParameter("receiver", "String", defaultValueForCall)
    }

    fun testAddPropertyReceiverConflict2() = doTestConflict {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"\"")
        receiverParameterInfo = createKotlinParameter("receiver", "String", defaultValueForCall)
    }

    fun testAddPropertyReceiver() = doTestAndIgnoreConflicts {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"\"")
        receiverParameterInfo = createKotlinParameter("receiver", "String", defaultValueForCall)
    }

    fun testGetConventionAddParameter() = doTest {
        addParameter(createKotlinParameter("b", originalType = "Boolean", KtPsiFactory(project).createExpression("false")))
    }


    fun testInvokeConventionAddParameter() = doTest {
        addParameter(createKotlinParameter("b", "Boolean", KtPsiFactory(project).createExpression("false")))
    }

    fun testPropagateWithParameterDuplication() = doTestConflict {
        val defaultValueForCall = KtPsiFactory(project).createExpression("1")
        addParameter(createKotlinIntParameter(name = "n", defaultValueForCall = defaultValueForCall))

        primaryPropagationTargets = listOf(
          KotlinTopLevelFunctionFqnNameIndex.get("bar", project, project.allScope()).first()
        )
    }

    fun testPropagateWithVariableDuplication() = doTestConflict {
        val defaultValueForCall = KtPsiFactory(project).createExpression("1")
        addParameter(createKotlinIntParameter(name = "n", defaultValueForCall = defaultValueForCall))

        primaryPropagationTargets = listOf(
          KotlinTopLevelFunctionFqnNameIndex.get("bar", project, project.allScope()).first()
        )
    }

    fun testPropagateWithThisQualificationInClassMember() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("1")
        addParameter(createKotlinIntParameter(name = "n", defaultValueForCall = defaultValueForCall))

        val classA = KotlinFullClassNameIndex.get("A", project, project.allScope()).first()
        val functionBar = classA.declarations.first { it is KtNamedFunction && it.name == "bar" }
        primaryPropagationTargets = listOf(functionBar)
    }

    fun testPropagateWithThisQualificationInExtension() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("1")
        addParameter(createKotlinIntParameter(name = "n", defaultValueForCall = defaultValueForCall))

        primaryPropagationTargets = listOf(
          KotlinTopLevelFunctionFqnNameIndex.get("bar", project, project.allScope()).first()
        )
    }

    fun testPrimaryConstructorParameterPropagation() = doTestAndIgnoreConflicts {
        val defaultValueForCall = KtPsiFactory(project).createExpression("1")
        addParameter(createKotlinIntParameter(name = "n", defaultValueForCall = defaultValueForCall))

        primaryPropagationTargets = findCallers(method.getRepresentativeLightMethod()!!)
    }

    fun testSecondaryConstructorParameterPropagation() = doTestAndIgnoreConflicts {
        val defaultValueForCall = KtPsiFactory(project).createExpression("1")
        addParameter(createKotlinIntParameter(name = "n", defaultValueForCall = defaultValueForCall))

        primaryPropagationTargets = findCallers(method.getRepresentativeLightMethod()!!)
    }

    fun testParameterPropagation() = doTestAndIgnoreConflicts {
        val psiFactory = KtPsiFactory(project)

        val defaultValueForCall1 = psiFactory.createExpression("1")
        val newParameter1 = createKotlinParameter("n", null, defaultValueForCall1, currentType = "Int")
        addParameter(newParameter1)

        val defaultValueForCall2 = psiFactory.createExpression("\"abc\"")
        val newParameter2 = createKotlinParameter("s", null, defaultValueForCall2, currentType = "String")
        addParameter(newParameter2)

        val classA = KotlinFullClassNameIndex.get("A", project, project.allScope()).first()
        val functionBar = classA.declarations.first { it is KtNamedFunction && it.name == "bar" }
        val functionTest = KotlinTopLevelFunctionFqnNameIndex.get("test", project, project.allScope()).first()

        primaryPropagationTargets = listOf(functionBar, functionTest)
    }

    fun testFunctionsAddRemoveArgumentsConflict2() = doTestConflict {
        setNewVisibility(Internal)

        val defaultValueForCall = KtPsiFactory(project).createExpression("null")
        val newParameters = newParameters
        setNewParameter(2, newParameters[1])
        setNewParameter(1, newParameters[0])
        setNewParameter(
          index = 0,
          createKotlinParameter("x0", "Any?", defaultValueForCall)
        )
    }


    fun testFunctionJavaUsagesAndOverridesAddParam() = doTestAndIgnoreConflicts {
        val psiFactory = KtPsiFactory(project)
        val defaultValueForCall1 = psiFactory.createExpression("\"abc\"")
        val defaultValueForCall2 = psiFactory.createExpression("\"def\"")
        addParameter(createKotlinStringParameter(defaultValueForCall = defaultValueForCall1))
        addParameter(createKotlinParameter("o", "Any?", defaultValueForCall2))
    }


    fun testParameterListAddParam() = doTest {
        addParameter(createKotlinParameter("l", "Long"))
    }

    fun testAddConstructorVisibility() = doTest {
        setNewVisibility(Protected)

        val newParameter = createKotlinParameter("x", "Any", KtPsiFactory(project).createExpression("12")).apply {
            valOrVar = KotlinValVar.Val
        }
        addParameter(newParameter)
    }

    fun testChangeConstructorPropertyWithChild() = doTest {
        val parameterInfo = newParameters[0]
        parameterInfo.name = "b"
        parameterInfo.setType("Int")
    }

    fun testChangeConstructorPropertyWithChild2() = doTest {
        val parameterInfo = newParameters[0]
        parameterInfo.name = "b"
        parameterInfo.setType("Int")
    }


    fun testSetErrorReceiverType() = doTest { receiverParameterInfo!!.setType("XYZ") }

    fun testSetErrorParameterType() = doTest { newParameters[1].setType("XYZ") }


    fun testImplicitThisToParameterWithChangedType() = doTest {
        receiverParameterInfo!!.setType("Older")
        receiverParameterInfo = null
    }

    fun testChangePropertyReceiver() = doTest {
        receiverParameterInfo!!.setType("Int")
    }

    fun testChangeTopLevelPropertyReceiver() = doTest {
        receiverParameterInfo!!.setType("String")
    }

    fun testAddReceiverToGenericsWithOverrides() = doTest {
        val parameterInfo = newParameters[0]
        parameterInfo.setType("U<A>")
        receiverParameterInfo = parameterInfo
    }


    fun testChangeParameterTypeWithImport() = doTest {
        newParameters[0].setType("a.Bar")
    }



    fun testConstructor() = doTest {
        setNewVisibility(Public)

        newParameters[0].valOrVar = KotlinValVar.Var
        newParameters[1].valOrVar = KotlinValVar.None
        newParameters[2].valOrVar = KotlinValVar.Val

        newParameters[0].name = "_x1"
        newParameters[1].name = "_x2"
        newParameters[2].name = "_x3"

        newParameters[1].setType("Float?")
    }

    fun testGenericConstructor() = doTest {
        setNewVisibility(Public)

        newParameters[0].valOrVar = KotlinValVar.Var
        newParameters[1].valOrVar = KotlinValVar.None
        newParameters[2].valOrVar = KotlinValVar.Val

        newParameters[0].name = "_x1"
        newParameters[1].name = "_x2"
        newParameters[2].name = "_x3"

        newParameters[1].setType("Double?")
    }



    fun testFunctions() = doTest {
        setNewVisibility(Public)

        newParameters[0].name = "_x1"
        newParameters[1].name = "_x2"
        newParameters[2].name = "_x3"

        newParameters[1].setType("Float?")
    }

    fun testGenericFunctions() = doTest {
        setNewVisibility(Public)

        newParameters[0].name = "_x1"
        newParameters[1].name = "_x2"
        newParameters[2].name = "_x3"

        newParameters[1].setType("Double?")
    }

    fun testFunctionLiteral() = doTest {
        newParameters[1].name = "y1"
        addParameter(createKotlinParameter("x", "Any"))

        setType("Int")
    }

    fun testFunctionJavaUsagesAndOverridesChangeNullability() = doTest {
        newParameters[1].setType("String?")
        newParameters[2].setType("Any")

        setType("String?")
    }

    fun testFunctionJavaUsagesAndOverridesChangeTypes() = doTest {
        newParameters[0].setType("String?")
        newParameters[1].setType("Int")
        newParameters[2].setType("Long?")

        setType("Any?")
    }

    fun testGenericsWithOverrides() = doTest {
        newParameters[0].setType("List<C>")
        newParameters[1].setType("A?")
        newParameters[2].setType("U<B>")

        setType("U<C>?")
    }



    fun testChangeProperty() = doTest {
        newName = "s"
        setType("String")
    }



    fun testChangeClassParameter() = doTest {
        newName = "s"
        setType("String")
    }

    fun testSetErrorReturnType() = doTest {
        setType("XYZ")
    }

    fun testChangeReturnTypeToNonUnit() = doTest {
        setType("Int")
    }


    fun testChangeReturnType() = doTest { setType("Float") }

    fun testAddReturnType() = doTest { setType("Float") }


    fun testRemoveReturnType() = doTest { setType("Unit") }


    fun testReturnTypeViaCodeFragment() = doTest {
        newName = "bar"
        setType("A<T, U>")
    }
}