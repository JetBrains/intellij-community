// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.changeSignature

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.TargetElementUtil.ELEMENT_NAME_ACCEPTED
import com.intellij.codeInsight.TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor
import com.intellij.refactoring.changeSignature.ParameterInfoImpl
import com.intellij.refactoring.util.CanonicalTypes
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.VisibilityUtil
import junit.framework.ComparisonFailure
import junit.framework.TestCase
import org.jetbrains.kotlin.asJava.getRepresentativeLightMethod
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.core.getDeepestSuperDeclarations
import org.jetbrains.kotlin.idea.intentions.AddFullQualifierIntention
import org.jetbrains.kotlin.idea.refactoring.changeSignature.ui.KotlinCallableParameterTableModel
import org.jetbrains.kotlin.idea.refactoring.changeSignature.ui.KotlinChangeSignatureDialog.Companion.getTypeInfo
import org.jetbrains.kotlin.idea.refactoring.changeSignature.ui.KotlinMethodNode
import org.jetbrains.kotlin.idea.search.allScope
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionFqnNameIndex
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.types.typeUtil.makeNullable
import org.jetbrains.kotlin.utils.addToStdlib.assertedCast
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlin.utils.sure
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File
import kotlin.reflect.full.declaredMemberFunctions

@RunWith(JUnit38ClassRunner::class)
class KotlinChangeSignatureTest : KotlinLightCodeInsightFixtureTestCase() {
    companion object {
        internal val BUILT_INS = DefaultBuiltIns.Instance
        private val EXTENSIONS = arrayOf(".kt", ".java")
    }

    override val testDataDirectory: File
        get() = IDEA_TEST_DATA_DIR.resolve("refactoring/changeSignature")

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE

    override fun setUp() {
        super.setUp()
        CodeStyle.getSettings(project).clearCodeStyleSettings()
    }

    override fun tearDown() {
        files = emptyList()
        psiFiles = PsiFile.EMPTY_ARRAY
        super.tearDown()
    }

    private lateinit var files: List<String>
    private lateinit var psiFiles: Array<PsiFile>

    private fun findCallers(method: PsiMethod): LinkedHashSet<PsiMethod> {
        val root = KotlinMethodNode(method, HashSet(), project) { }
        return (0 until root.childCount).flatMapTo(LinkedHashSet()) {
            (root.getChildAt(it) as KotlinMethodNode).member.toLightMethods()
        }
    }

    private fun configureFiles() {
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

    private fun findTargetElement(): PsiElement? = KotlinChangeSignatureHandler().findTargetMember(file, editor)

    private class ChangeSignatureContext(
        val callableDescriptor: CallableDescriptor,
        val context: KtElement,
    )

    private fun createChangeSignatureContext(): ChangeSignatureContext {
        val element = findTargetElement().assertedCast<KtElement> { "Target element is null" }
        val context = file
            .findElementAt(editor.caretModel.offset)
            ?.getNonStrictParentOfType<KtElement>()
            .sure { "Context element is null" }

        val bindingContext = element.analyze(BodyResolveMode.FULL)
        val callableDescriptor = KotlinChangeSignatureHandler
            .findDescriptor(element, project, editor, bindingContext)
            .sure { "Target descriptor is null" }

        return ChangeSignatureContext(callableDescriptor, context)
    }

    private fun createChangeInfo(): KotlinChangeInfo {
        val configuration = createChangeSignatureContext()
        return createChangeInfo(
            project,
            editor,
            configuration.callableDescriptor,
            KotlinChangeSignatureConfiguration.Empty,
            configuration.context,
        )!!
    }

    private fun doTest(configure: KotlinChangeInfo.() -> Unit = {}) {
        configureFiles()
        doRefactoring(configure)
        compareEditorsWithExpectedData()
    }

    private fun doRefactoring(configure: KotlinChangeInfo.() -> Unit) {
        KotlinChangeSignatureProcessor(project, createChangeInfo().apply { configure() }, "Change Signature").run()
    }

    private fun doTestAndIgnoreConflicts(configure: KotlinChangeInfo.() -> Unit = {}) = withIgnoredConflicts<Throwable> {
        doTest(configure)
    }

    private fun runAndCheckConflicts(testAction: () -> Unit) {
        try {
            testAction()
            TestCase.fail("No conflicts found")
        } catch (e: Throwable) {
            val message = when {
                e is BaseRefactoringProcessor.ConflictsInTestsException -> StringUtil.join(e.messages.sorted(), "\n")
                e is CommonRefactoringUtil.RefactoringErrorHintException -> e.message
                e is RuntimeException && e.message!!.startsWith("Refactoring cannot be performed") -> e.message
                else -> throw e
            }
            val conflictsFile = File(testDataDirectory, getTestName(false) + "Messages.txt")
            UsefulTestCase.assertSameLinesWithFile(conflictsFile.absolutePath, message!!)
        }
    }

    private fun doTestConflict(configure: KotlinChangeInfo.() -> Unit = {}) = runAndCheckConflicts { doTest(configure) }

    private fun doTestUnmodifiable(configure: KotlinChangeInfo.() -> Unit = {}) {
        try {
            doTest(configure)
            TestCase.fail("No conflicts found")
        } catch (e: RuntimeException) {
            if ((e.message ?: "").contains("Cannot modify file")) return

            val message = when {
                e is BaseRefactoringProcessor.ConflictsInTestsException -> StringUtil.join(e.messages.sorted(), "\n")
                e is CommonRefactoringUtil.RefactoringErrorHintException -> e.message
                e.message!!.startsWith("Refactoring cannot be performed") -> e.message
                else -> throw e
            }
            val conflictsFile = File(testDataDirectory, getTestName(false) + "Messages.txt")
            UsefulTestCase.assertSameLinesWithFile(conflictsFile.absolutePath, message!!)
        }
    }

    private class JavaRefactoringConfiguration(val method: PsiMethod) {
        val project = method.project

        var newName = method.name
        var newReturnType: PsiType = method.returnType ?: PsiType.VOID
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

    private fun doJavaTest(configure: JavaRefactoringConfiguration.() -> Unit) {
        configureFiles()

        val targetElement = TargetElementUtil.findTargetElement(editor, ELEMENT_NAME_ACCEPTED or REFERENCED_ELEMENT_ACCEPTED)
        val targetMethod = (targetElement as? PsiMethod).sure { "<caret> is not on method name" }

        JavaRefactoringConfiguration(targetMethod).apply { configure() }.createProcessor().run()

        compareEditorsWithExpectedData()
    }

    private fun doJavaTestConflict(configure: JavaRefactoringConfiguration.() -> Unit) = runAndCheckConflicts { doJavaTest(configure) }

    private fun compareEditorsWithExpectedData() {
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

    private fun KotlinChangeInfo.swapParameters(i: Int, j: Int) {
        val newParameters = newParameters
        val temp = newParameters[i]
        setNewParameter(i, newParameters[j])
        setNewParameter(j, temp)
    }

    private fun KotlinChangeInfo.resolveType(text: String, isCovariant: Boolean, forPreview: Boolean): KotlinTypeInfo =
        KtPsiFactory(project).createTypeCodeFragment(
            text,
            KotlinCallableParameterTableModel.getTypeCodeFragmentContext(context),
        ).getTypeInfo(isCovariant, forPreview)

    private inline fun <reified T> doTestTargetElement(code: String, withError: Boolean = false) {
        myFixture.configureByText("dummy.kt", code)
        val element = findTargetElement()!!
        TestCase.assertEquals(T::class, element::class)

        val exception = try {
            doRefactoring { }
            null
        } catch (e: CommonRefactoringUtil.RefactoringErrorHintException) {
            e
        }

        if (withError) {
            TestCase.assertNotNull("No errors found", exception)
        } else {
            exception?.let { throw it }
        }

        val expected = code.replace("<caret>", "")
        TestCase.assertEquals(expected, file.text)
    }

    private fun KotlinMutableMethodDescriptor.createNewParameter(
        name: String = "i",
        type: KotlinTypeInfo = kotlinIntType,
        callableDescriptor: CallableDescriptor = baseDescriptor,
        defaultValueForCall: KtExpression? = null,
        defaultValueAsDefaultParameter: Boolean = false,
    ): KotlinParameterInfo = KotlinParameterInfo(
        callableDescriptor = callableDescriptor,
        name = name,
        originalTypeInfo = type,
        defaultValueForCall = defaultValueForCall,
        defaultValueAsDefaultParameter = defaultValueAsDefaultParameter,
    )

    private fun KotlinMutableMethodDescriptor.createNewIntParameter(
        defaultValueForCall: KtExpression? = null,
        withDefaultValue: Boolean = false,
    ): KotlinParameterInfo = createNewParameter(
        name = "i",
        type = kotlinIntType,
        callableDescriptor = baseDescriptor,
        defaultValueForCall = defaultValueForCall,
    ).apply {
        if (withDefaultValue) {
            defaultValueAsDefaultParameter = true
            this.defaultValueForCall = defaultValueForCall ?: kotlinDefaultIntValue
        }
    }

    private fun KotlinMutableMethodDescriptor.addNewIntParameterWithValue(asParameter: Boolean, index: Int? = null) {
        val newIntParameter = createNewIntParameter(defaultValueForCall = kotlinDefaultIntValue, withDefaultValue = asParameter)
        if (index != null) {
            addParameter(index, newIntParameter)
        } else {
            addParameter(newIntParameter)
        }
    }

    private fun doTestWithDescriptorModification(configureFiles: Boolean = true, modificator: KotlinMutableMethodDescriptor.() -> Unit) {
        if (configureFiles) {
            configureFiles()
        }

        val context = createChangeSignatureContext()
        val callableDescriptor = context.callableDescriptor
        val kotlinChangeSignature = KotlinChangeSignature(
            project,
            editor,
            callableDescriptor,
            object : KotlinChangeSignatureConfiguration {
                override fun configure(originalDescriptor: KotlinMethodDescriptor): KotlinMethodDescriptor {
                    return originalDescriptor.modify(modificator)
                }
            },
            context.context,
            null,
        )

        val declarations = callableDescriptor.safeAs<CallableMemberDescriptor>()
            ?.getDeepestSuperDeclarations()
            ?: listOf(callableDescriptor)

        val adjustedDescriptor = kotlinChangeSignature.adjustDescriptor(declarations)!!
        val processor = kotlinChangeSignature.createSilentRefactoringProcessor(adjustedDescriptor) as KotlinChangeSignatureProcessor
        processor.ktChangeInfo.also { it.checkUsedParameters = true }
        processor.run()

        compareEditorsWithExpectedData()
    }

    private fun createExpressionWithImports(context: PsiElement, expression: String, imports: List<String>): KtExpression? {
        val fragment = KtPsiFactory(project).createExpressionCodeFragment(expression, context)
        project.executeWriteCommand("add imports and qualifiers") {
            fragment.addImportsFromString(imports.joinToString(separator = KtCodeFragment.IMPORT_SEPARATOR) { "import $it" })
            AddFullQualifierIntention.addQualifiersRecursively(fragment)
        }

        return fragment.getContentElement()
    }

    private val kotlinFloatType get() = KotlinTypeInfo(true, BUILT_INS.floatType)
    private val kotlinUnitType get() = KotlinTypeInfo(true, BUILT_INS.unitType)
    private val kotlinIntType get() = KotlinTypeInfo(true, BUILT_INS.intType)
    private val kotlinAnyType get() = KotlinTypeInfo(false, BUILT_INS.anyType)
    private val kotlinStringType get() = KotlinTypeInfo(false, BUILT_INS.stringType)
    private val kotlinDefaultIntValue: KtExpression get() = KtPsiFactory(project).createExpression("42")

    private fun KotlinChangeInfo.createKotlinStringParameter(defaultValueForCall: KtExpression? = null) = createKotlinParameter(
        name = "s",
        type = kotlinStringType,
        defaultValueForCall = defaultValueForCall,
    )

    private fun KotlinChangeInfo.createKotlinIntParameter(
        name: String = "i",
        defaultValueForCall: KtExpression? = null,
        defaultValueAsDefaultParameter: Boolean = false,
    ) = createKotlinParameter(
        name = name,
        type = kotlinIntType,
        defaultValueForCall = defaultValueForCall,
        defaultValueAsDefaultParameter = defaultValueAsDefaultParameter,
    )

    private fun KotlinChangeInfo.createKotlinParameter(
        name: String,
        type: KotlinTypeInfo,
        defaultValueForCall: KtExpression? = null,
        defaultValueAsDefaultParameter: Boolean = false,
    ): KotlinParameterInfo = KotlinParameterInfo(
        callableDescriptor = originalBaseFunctionDescriptor,
        name = name,
        originalTypeInfo = type,
        defaultValueForCall = defaultValueForCall,
        defaultValueAsDefaultParameter = defaultValueAsDefaultParameter,
    )
    // --------------------------------- Tests ---------------------------------

    fun testAllTestsPresented() {
        val functionNames = this::class.declaredMemberFunctions
            .asSequence()
            .map { it.name }
            .filter { it.startsWith("test") }
            .map { it.removePrefix("test") }
            .map { it + "Before" }
            .toSet()

        for (file in testDataDirectory.listFiles()!!) {
            val fileName = file.name.substringBefore(".")
            if (fileName.endsWith("Messages") || fileName.endsWith("After")) continue

            TestCase.assertTrue(
                "test function for ${file.name} not found",
                fileName in functionNames,
            )
        }
    }

    fun testBadSelection() {
        myFixture.configureByFile(getTestName(false) + "Before.kt")
        TestCase.assertNull(findTargetElement())
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

    fun testAddNewLastParameterWithDefaultValueToJava() = doTestConflict {
        addParameter(createKotlinIntParameter(defaultValueAsDefaultParameter = true))
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

    fun testCaretAtReferenceAsValueParameter() = doTestConflict()

    fun testSynthesized() = doTestConflict()

    fun testPreferContainedInClass() {
        configureFiles()
        TestCase.assertEquals("param", createChangeInfo().newParameters[0].name)
    }

    fun testRenameFunction() = doTest { newName = "after" }

    fun testChangeReturnType() = doTest { newReturnTypeInfo = kotlinFloatType }

    fun testAddReturnType() = doTest { newReturnTypeInfo = kotlinFloatType }

    fun testRemoveReturnType() = doTest { newReturnTypeInfo = kotlinUnitType }

    fun testChangeConstructorVisibility() = doTest { newVisibility = DescriptorVisibilities.PROTECTED }

    fun testChangeConstructorPropertyWithChild() = doTest {
        val parameterInfo = newParameters[0]
        parameterInfo.name = "b"
        parameterInfo.currentTypeInfo = kotlinIntType
    }

    fun testChangeConstructorPropertyWithChild2() = doTest {
        val parameterInfo = newParameters[0]
        parameterInfo.name = "b"
        parameterInfo.currentTypeInfo = kotlinIntType
    }

    fun testAddConstructorVisibility() = doTest {
        newVisibility = DescriptorVisibilities.PROTECTED

        val newParameter = KotlinParameterInfo(
            callableDescriptor = originalBaseFunctionDescriptor,
            name = "x",
            originalTypeInfo = kotlinAnyType,
            defaultValueForCall = KtPsiFactory(project).createExpression("12"),
            valOrVar = KotlinValVar.Val
        )
        addParameter(newParameter)
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

    fun testConstructor() = doTest {
        newVisibility = DescriptorVisibilities.PUBLIC

        newParameters[0].valOrVar = KotlinValVar.Var
        newParameters[1].valOrVar = KotlinValVar.None
        newParameters[2].valOrVar = KotlinValVar.Val

        newParameters[0].name = "_x1"
        newParameters[1].name = "_x2"
        newParameters[2].name = "_x3"

        newParameters[1].currentTypeInfo = KotlinTypeInfo(false, BUILT_INS.floatType.makeNullable())
    }

    fun testGenericConstructor() = doTest {
        newVisibility = DescriptorVisibilities.PUBLIC

        newParameters[0].valOrVar = KotlinValVar.Var
        newParameters[1].valOrVar = KotlinValVar.None
        newParameters[2].valOrVar = KotlinValVar.Val

        newParameters[0].name = "_x1"
        newParameters[1].name = "_x2"
        newParameters[2].name = "_x3"

        newParameters[1].currentTypeInfo = KotlinTypeInfo(false, BUILT_INS.doubleType.makeNullable())
    }

    fun testConstructorSwapArguments() = doTest {
        newParameters[0].name = "_x1"
        newParameters[1].name = "_x2"

        swapParameters(0, 2)
    }

    fun testFunctions() = doTest {
        newVisibility = DescriptorVisibilities.PUBLIC

        newParameters[0].name = "_x1"
        newParameters[1].name = "_x2"
        newParameters[2].name = "_x3"

        newParameters[1].currentTypeInfo = KotlinTypeInfo(false, BUILT_INS.floatType.makeNullable())
    }

    fun testGenericFunctions() = doTest {
        newVisibility = DescriptorVisibilities.PUBLIC

        newParameters[0].name = "_x1"
        newParameters[1].name = "_x2"
        newParameters[2].name = "_x3"

        newParameters[1].currentTypeInfo = KotlinTypeInfo(false, BUILT_INS.doubleType.makeNullable())
    }

    fun testExpressionFunction() = doTest {
        newParameters[0].name = "x1"

        addParameter(KotlinParameterInfo(originalBaseFunctionDescriptor, -1, "y1", kotlinIntType))
    }

    fun testFunctionsAddRemoveArgumentsConflict() = doTestConflict {
        newVisibility = DescriptorVisibilities.INTERNAL

        val defaultValueForCall = KtPsiFactory(project).createExpression("null")
        val newParameters = newParameters
        setNewParameter(2, newParameters[1])
        setNewParameter(1, newParameters[0])
        setNewParameter(
            index = 0,
            parameterInfo = KotlinParameterInfo(
                originalBaseFunctionDescriptor,
                name = "x0",
                originalTypeInfo = KotlinTypeInfo(false, BUILT_INS.nullableAnyType),
                defaultValueForCall = defaultValueForCall,
            )
        )
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

    fun testFunctionFromStdlibConflict() = doTestUnmodifiable()

    fun testExtensionFromStdlibConflict() = doTestUnmodifiable()

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

    fun testFunctionsAddRemoveArgumentsConflict2() = doTestConflict {
        newVisibility = DescriptorVisibilities.INTERNAL

        val defaultValueForCall = KtPsiFactory(project).createExpression("null")
        val newParameters = newParameters
        setNewParameter(2, newParameters[1])
        setNewParameter(1, newParameters[0])
        setNewParameter(
            index = 0,
            parameterInfo = KotlinParameterInfo(
                callableDescriptor = originalBaseFunctionDescriptor,
                name = "x0",
                originalTypeInfo = KotlinTypeInfo(false, BUILT_INS.nullableAnyType),
                defaultValueForCall = defaultValueForCall,
            )
        )
    }

    fun testFakeOverride() = doTest {
        addParameter(createKotlinIntParameter())
    }

    fun testFunctionLiteral() = doTest {
        newParameters[1].name = "y1"
        addParameter(KotlinParameterInfo(originalBaseFunctionDescriptor, -1, "x", kotlinAnyType))

        newReturnTypeInfo = kotlinIntType
    }

    fun testVarargs() = doTestConflict()

    fun testUnmodifiableFromLibrary() = doTestUnmodifiable()

    fun testUnmodifiableFromBuiltins() = doTestUnmodifiable()

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

    fun testNoDefaultValuesInOverrides() = doTest { swapParameters(0, 1) }

    fun testOverridesInEnumEntries() = doTest {
        addParameter(createKotlinStringParameter())
    }

    fun testRemoveParameterFromFunctionWithReceiver() = doJavaTest { newParameters.clear() }

    fun testAddParameterFromFunctionWithReceiver() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "i", PsiType.INT)) }

    fun testChangeJavaMethod() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "i", PsiType.INT)) }

    fun testChangeJavaMethodWithPrimitiveType() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "s", stringPsiType)) }

    fun testChangeJavaMethodWithBoxedType() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "s", stringPsiType)) }

    fun testChangeJavaMethodWithFlexibleMutableType() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "s", stringPsiType)) }

    fun testChangeJavaMethodWithNestedFlexibleType() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "i", PsiType.INT)) }

    fun testChangeJavaMethodWithFlexibleMutableType1() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "s", stringPsiType)) }

    fun testChangeJavaMethodWithRawType() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "i", PsiType.INT)) }

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

    private fun javaIntegerParameter() = ParameterInfoImpl(-1, "integer", PsiType.INT)

    fun testEnumEntriesWithoutSuperCalls() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("1")
        addParameter(createKotlinIntParameter(name = "n", defaultValueForCall = defaultValueForCall))
    }

    fun testParameterChangeInOverrides() = doTest {
        newParameters[0].name = "n"
        newParameters[0].currentTypeInfo = kotlinIntType
    }

    fun testConstructorJavaUsages() = doTestAndIgnoreConflicts {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"abc\"")
        addParameter(createKotlinStringParameter(defaultValueForCall))
    }

    fun testFunctionJavaUsagesAndOverridesAddParam() = doTestAndIgnoreConflicts {
        val psiFactory = KtPsiFactory(project)
        val defaultValueForCall1 = psiFactory.createExpression("\"abc\"")
        val defaultValueForCall2 = psiFactory.createExpression("\"def\"")
        addParameter(createKotlinStringParameter(defaultValueForCall1))
        addParameter(
            KotlinParameterInfo(
                callableDescriptor = originalBaseFunctionDescriptor,
                name = "o",
                originalTypeInfo = KotlinTypeInfo(false, BUILT_INS.nullableAnyType),
                defaultValueForCall = defaultValueForCall2,
            )
        )
    }

    fun testFunctionJavaUsagesAndOverridesChangeNullability() = doTest {
        newParameters[1].currentTypeInfo = KotlinTypeInfo(false, BUILT_INS.stringType.makeNullable())
        newParameters[2].currentTypeInfo = kotlinAnyType

        newReturnTypeInfo = KotlinTypeInfo(true, BUILT_INS.stringType.makeNullable())
    }

    fun testFunctionJavaUsagesAndOverridesChangeTypes() = doTest {
        newParameters[0].currentTypeInfo = KotlinTypeInfo(false, BUILT_INS.stringType.makeNullable())
        newParameters[1].currentTypeInfo = kotlinIntType
        newParameters[2].currentTypeInfo = KotlinTypeInfo(false, BUILT_INS.longType.makeNullable())

        newReturnTypeInfo = KotlinTypeInfo(true, BUILT_INS.nullableAnyType)
    }

    fun testGenericsWithOverrides() = doTest {
        newParameters[0].currentTypeInfo = KotlinTypeInfo(false, null, "List<C>")
        newParameters[1].currentTypeInfo = KotlinTypeInfo(false, null, "A?")
        newParameters[2].currentTypeInfo = KotlinTypeInfo(false, null, "U<B>")

        newReturnTypeInfo = KotlinTypeInfo(true, null, "U<C>?")
    }

    fun testAddReceiverToGenericsWithOverrides() = doTest {
        val parameterInfo = newParameters[0]
        parameterInfo.currentTypeInfo = KotlinTypeInfo(false, null, "U<A>")
        receiverParameterInfo = parameterInfo
    }

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

    fun testSAMAddToSingletonParamList() = doJavaTest { newParameters.add(0, ParameterInfoImpl(-1, "n", PsiType.INT)) }

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

    fun testFunctionRenameJavaUsages() = doTest { newName = "bar" }

    fun testParameterModifiers() = doTest {
        addParameter(createKotlinIntParameter(name = "n"))
    }

    fun testFqNameShortening() = doTest {
        val newParameter = KotlinParameterInfo(
            callableDescriptor = originalBaseFunctionDescriptor,
            name = "s",
            originalTypeInfo = kotlinAnyType,
        ).apply {
            currentTypeInfo = KotlinTypeInfo(false, null, "kotlin.String")
        }
        addParameter(newParameter)
    }

    fun testObjectMember() = doTest { removeParameter(0) }

    fun testParameterListAddParam() = doTest {
        addParameter(
            KotlinParameterInfo(
                callableDescriptor = originalBaseFunctionDescriptor,
                name = "l",
                originalTypeInfo = KotlinTypeInfo(false, BUILT_INS.longType)
            )
        )
    }

    fun testParameterListRemoveParam() = doTest { removeParameter(getNewParametersCount() - 1) }

    fun testParameterListRemoveAllParams() = doTest { clearParameters() }

    fun testAddNewReceiver() = doTestAndIgnoreConflicts {
        val defaultValueForCall = KtPsiFactory(project).createExpression("X(0)")
        receiverParameterInfo = KotlinParameterInfo(
            callableDescriptor = originalBaseFunctionDescriptor,
            name = "_",
            originalTypeInfo = kotlinAnyType,
            defaultValueForCall = defaultValueForCall,
        ).apply { currentTypeInfo = KotlinTypeInfo(false, null, "X") }
    }

    fun testAddNewReceiverForMember() = doTestAndIgnoreConflicts {
        val defaultValueForCall = KtPsiFactory(project).createExpression("X(0)")
        receiverParameterInfo = KotlinParameterInfo(
            callableDescriptor = originalBaseFunctionDescriptor,
            name = "_",
            originalTypeInfo = kotlinAnyType,
            defaultValueForCall = defaultValueForCall,
        ).apply { currentTypeInfo = KotlinTypeInfo(false, null, "X") }
    }

    fun testAddNewReceiverForMemberConflict() = doTestConflict {
        val defaultValueForCall = KtPsiFactory(project).createExpression("X(0)")
        receiverParameterInfo = KotlinParameterInfo(
            callableDescriptor = originalBaseFunctionDescriptor,
            name = "_",
            originalTypeInfo = kotlinAnyType,
            defaultValueForCall = defaultValueForCall,
        ).apply { currentTypeInfo = KotlinTypeInfo(false, null, "X") }
    }

    fun testAddNewReceiverConflict() = doTestConflict {
        val defaultValueForCall = KtPsiFactory(project).createExpression("X(0)")
        receiverParameterInfo = KotlinParameterInfo(
            callableDescriptor = originalBaseFunctionDescriptor,
            name = "_",
            originalTypeInfo = kotlinAnyType,
            defaultValueForCall = defaultValueForCall,
        ).apply { currentTypeInfo = KotlinTypeInfo(false, null, "X") }
    }

    fun testRemoveReceiver() = doTest { removeParameter(0) }

    fun testRemoveReceiverConflict() = doTestConflict { removeParameter(0) }

    fun testRemoveReceiverInParentConflict() = doTestConflict { removeParameter(0) }

    fun testRemoveReceiverInParentConflict2() = doTestConflict { removeParameter(0) }

    fun testRemoveReceiverInParentConflict3() = doTestConflict { removeParameter(0) }

    fun testRemoveReceiverInParentConflict4() = doTestConflict { removeParameter(0) }

    fun testRemoveReceiverForMember() = doTest { removeParameter(0) }

    fun testRemoveReceiverForMemberConflict() = doTestConflict { removeParameter(0) }

    fun testConvertParameterToReceiver1() = doTest { receiverParameterInfo = newParameters[0] }

    fun testConvertParameterToReceiver2() = doTest { receiverParameterInfo = newParameters[1] }

    fun testConvertReceiverToParameter1() = doTest { receiverParameterInfo = null }

    fun testConvertReceiverToParameter2() = doTest {
        receiverParameterInfo = null

        val newParameters = newParameters
        setNewParameter(0, newParameters[1])
        setNewParameter(1, newParameters[0])
    }

    fun testConvertParameterToReceiverForMember1() = doTest { receiverParameterInfo = newParameters[0] }

    fun testConvertParameterToReceiverForMemberUltraLight() = doTest { receiverParameterInfo = newParameters[0] }

    fun testConvertParameterToReceiverForMember2() = doTest { receiverParameterInfo = newParameters[1] }

    fun testConvertParameterToReceiverForMemberConflict() = doTestConflict { receiverParameterInfo = newParameters[0] }

    fun testConvertReceiverToParameterForMember1() = doTest { receiverParameterInfo = null }

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

    fun testChangeParameterTypeWithImport() = doTest { newParameters[0].currentTypeInfo = KotlinTypeInfo(false, null, "a.Bar") }

    fun testSecondaryConstructor() = doTestAndIgnoreConflicts {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"foo\"")
        addParameter(createKotlinStringParameter(defaultValueForCall))
    }

    fun testPrimaryConstructorOnConstructorKeyword() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"foo\"")
        addParameter(createKotlinStringParameter(defaultValueForCall))
    }

    fun testJavaConstructorInDelegationCall() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "s", stringPsiType, "\"foo\"")) }

    fun testPrimaryConstructorByThisRef() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"foo\"")
        addParameter(createKotlinStringParameter(defaultValueForCall))
    }

    fun testPrimaryConstructorBySuperRef() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"foo\"")
        addParameter(createKotlinStringParameter(defaultValueForCall))
    }

    fun testSecondaryConstructorByThisRef() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"foo\"")
        addParameter(createKotlinStringParameter(defaultValueForCall))
    }

    fun testSecondaryConstructorBySuperRef() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"foo\"")
        addParameter(createKotlinStringParameter(defaultValueForCall))
    }

    fun testJavaConstructorBySuperRef() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "s", stringPsiType, "\"foo\"")) }

    fun testNoConflictWithReceiverName() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("0")
        addParameter(createKotlinIntParameter(defaultValueForCall = defaultValueForCall))
    }

    fun testRemoveParameterBeforeLambda() = doTest { removeParameter(1) }

    fun testMoveLambdaParameter() = doTest {
        val newParameters = newParameters
        setNewParameter(1, newParameters[2])
        setNewParameter(2, newParameters[1])
    }

    fun testConvertLambdaParameterToReceiver() = doTest { receiverParameterInfo = newParameters[2] }

    fun testRemoveLambdaParameter() = doTest { removeParameter(2) }

    fun testRemoveLambdaParameterConflict() = doTestConflict { removeParameter(2) }

    fun testRemoveEnumConstructorParameter() = doTest { removeParameter(1) }

    fun testRemoveAllEnumConstructorParameters() = doTest { clearParameters() }

    fun testDoNotApplyPrimarySignatureToSecondaryCalls() = doTest {
        val newParameters = newParameters
        setNewParameter(0, newParameters[1])
        setNewParameter(1, newParameters[0])
    }

    fun testConvertToExtensionAndRename() = doTest {
        receiverParameterInfo = newParameters[0]
        newName = "foo1"
    }

    fun testRenameExtensionParameter() = doTest { newParameters[1].name = "b" }

    fun testConvertParameterToReceiverAddParents() = doTest { receiverParameterInfo = newParameters[0] }

    fun testThisReplacement() = doTest { receiverParameterInfo = null }

    fun testPrimaryConstructorByRef() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("1")
        addParameter(createKotlinIntParameter(name = "n", defaultValueForCall = defaultValueForCall))
    }

    fun testReceiverToParameterExplicitReceiver() = doTest { receiverParameterInfo = null }

    fun testReceiverToParameterImplicitReceivers() = doTest { receiverParameterInfo = null }

    fun testParameterToReceiverExplicitReceiver() = doTest { receiverParameterInfo = newParameters[0] }

    fun testParameterToReceiverImplicitReceivers() = doTest { receiverParameterInfo = newParameters[0] }

    fun testJavaMethodOverridesReplaceParam() = doJavaTestConflict {
        newReturnType = stringPsiType
        newParameters[0] = ParameterInfoImpl(-1, "x", PsiType.INT, "1")
    }

    fun testJavaMethodOverridesChangeParam() = doJavaTest {
        newReturnType = stringPsiType

        newParameters[0].name = "x"
        newParameters[0].setType(PsiType.INT)
    }

    fun testChangeProperty() = doTest {
        newName = "s"
        newReturnTypeInfo = KotlinTypeInfo(true, BUILT_INS.stringType)
    }

    fun testAddPropertyReceiverConflict() = doTestConflict {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"\"")
        receiverParameterInfo = KotlinParameterInfo(
            callableDescriptor = originalBaseFunctionDescriptor,
            name = "receiver",
            originalTypeInfo = kotlinStringType,
            defaultValueForCall = defaultValueForCall,
        )
    }

    fun testAddPropertyReceiverConflict2() = doTestConflict {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"\"")
        receiverParameterInfo = KotlinParameterInfo(
            callableDescriptor = originalBaseFunctionDescriptor,
            name = "receiver",
            originalTypeInfo = kotlinStringType,
            defaultValueForCall = defaultValueForCall,
        )
    }

    fun testAddPropertyReceiver() = doTestAndIgnoreConflicts {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"\"")
        receiverParameterInfo = KotlinParameterInfo(
            callableDescriptor = originalBaseFunctionDescriptor,
            name = "receiver",
            originalTypeInfo = kotlinStringType,
            defaultValueForCall = defaultValueForCall,
        )
    }

    fun testChangePropertyReceiver() = doTest { receiverParameterInfo!!.currentTypeInfo = kotlinIntType }

    fun testRemovePropertyReceiver() = doTest { receiverParameterInfo = null }

    fun testAddTopLevelPropertyReceiver() = doTestAndIgnoreConflicts {
        val defaultValueForCall = KtPsiFactory(project).createExpression("A()")
        receiverParameterInfo = KotlinParameterInfo(
            callableDescriptor = originalBaseFunctionDescriptor,
            name = "receiver",
            originalTypeInfo = KotlinTypeInfo(false),
            defaultValueForCall = defaultValueForCall,
        ).apply { currentTypeInfo = KotlinTypeInfo(false, null, "test.A") }
    }

    fun testChangeTopLevelPropertyReceiver() = doTest {
        receiverParameterInfo!!.currentTypeInfo = kotlinStringType
    }

    fun testRemoveTopLevelPropertyReceiver() = doTest { receiverParameterInfo = null }

    fun testChangeClassParameter() = doTest {
        newName = "s"
        newReturnTypeInfo = KotlinTypeInfo(true, BUILT_INS.stringType)
    }

    fun testChangeClassParameterWithInvalidCharacter() = doTest {
        newParameters[0].name = "a@bc"
    }

    fun testParameterPropagation() = doTestAndIgnoreConflicts {
        val psiFactory = KtPsiFactory(project)

        val defaultValueForCall1 = psiFactory.createExpression("1")
        val newParameter1 = KotlinParameterInfo(
            callableDescriptor = originalBaseFunctionDescriptor,
            name = "n",
            originalTypeInfo = KotlinTypeInfo(false),
            defaultValueForCall = defaultValueForCall1,
        ).apply { currentTypeInfo = kotlinIntType }
        addParameter(newParameter1)

        val defaultValueForCall2 = psiFactory.createExpression("\"abc\"")
        val newParameter2 = KotlinParameterInfo(
            callableDescriptor = originalBaseFunctionDescriptor,
            name = "s",
            originalTypeInfo = KotlinTypeInfo(false),
            defaultValueForCall = defaultValueForCall2,
        ).apply { currentTypeInfo = kotlinStringType }
        addParameter(newParameter2)

        val classA = KotlinFullClassNameIndex.getInstance().get("A", project, project.allScope()).first()
        val functionBar = classA.declarations.first { it is KtNamedFunction && it.name == "bar" }
        val functionTest = KotlinTopLevelFunctionFqnNameIndex.getInstance().get("test", project, project.allScope()).first()

        primaryPropagationTargets = listOf(functionBar, functionTest)
    }

    fun testJavaParameterPropagation() = doJavaTest {
        newParameters.add(ParameterInfoImpl(-1, "n", PsiType.INT, "1"))
        newParameters.add(ParameterInfoImpl(-1, "s", stringPsiType, "\"abc\""))

        val classA = JavaFullClassNameIndex.getInstance().get("A", project, project.allScope()).first { it.name == "A" }
        val methodBar = classA.methods.first { it.name == "bar" }
        parameterPropagationTargets.add(methodBar)

        val functionTest = KotlinTopLevelFunctionFqnNameIndex.getInstance().get("test", project, project.allScope()).first()
        parameterPropagationTargets.add(functionTest.getRepresentativeLightMethod()!!)
    }

    fun testPropagateWithParameterDuplication() = doTestConflict {
        val defaultValueForCall = KtPsiFactory(project).createExpression("1")
        addParameter(createKotlinIntParameter(name = "n", defaultValueForCall = defaultValueForCall))

        primaryPropagationTargets = listOf(
            KotlinTopLevelFunctionFqnNameIndex.getInstance().get("bar", project, project.allScope()).first()
        )
    }

    fun testPropagateWithVariableDuplication() = doTestConflict {
        val defaultValueForCall = KtPsiFactory(project).createExpression("1")
        addParameter(createKotlinIntParameter(name = "n", defaultValueForCall = defaultValueForCall))

        primaryPropagationTargets = listOf(
            KotlinTopLevelFunctionFqnNameIndex.getInstance().get("bar", project, project.allScope()).first()
        )
    }

    fun testPropagateWithThisQualificationInClassMember() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("1")
        addParameter(createKotlinIntParameter(name = "n", defaultValueForCall = defaultValueForCall))

        val classA = KotlinFullClassNameIndex.getInstance().get("A", project, project.allScope()).first()
        val functionBar = classA.declarations.first { it is KtNamedFunction && it.name == "bar" }
        primaryPropagationTargets = listOf(functionBar)
    }

    fun testPropagateWithThisQualificationInExtension() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("1")
        addParameter(createKotlinIntParameter(name = "n", defaultValueForCall = defaultValueForCall))

        primaryPropagationTargets = listOf(
            KotlinTopLevelFunctionFqnNameIndex.getInstance().get("bar", project, project.allScope()).first()
        )
    }

    fun testJavaConstructorParameterPropagation() = doJavaTest {
        newParameters.add(ParameterInfoImpl(-1, "n", PsiType.INT, "1"))
        parameterPropagationTargets.addAll(findCallers(method))
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

    fun testJavaMethodOverridesOmitUnitType() = doJavaTest {}

    fun testOverrideInAnonymousObjectWithTypeParameters() = doTest { newName = "bar" }

    fun testMakePrimaryConstructorPrivateNoParams() = doTest { newVisibility = DescriptorVisibilities.PRIVATE }

    fun testMakePrimaryConstructorPublic() = doTest { newVisibility = DescriptorVisibilities.PUBLIC }

    fun testRenameExtensionParameterWithNamedArgs() = doTest { newParameters[2].name = "bb" }

    fun testImplicitThisToParameterWithChangedType() = doTest {
        receiverParameterInfo!!.currentTypeInfo = KotlinTypeInfo(false, null, "Older")
        receiverParameterInfo = null
    }

    fun testJvmOverloadedRenameParameter() = doTest { newParameters[0].name = "aa" }

    fun testJvmOverloadedSwapParams1() = doTestAndIgnoreConflicts { swapParameters(1, 2) }

    fun testJvmOverloadedSwapParams2() = doTestAndIgnoreConflicts { swapParameters(0, 2) }

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

    fun testJvmOverloadedRemoveDefault3() = doTestAndIgnoreConflicts { removeParameter(getNewParametersCount() - 1) }

    fun testJvmOverloadedRemoveNonDefault1() = doTestAndIgnoreConflicts { removeParameter(0) }

    fun testJvmOverloadedRemoveNonDefault2() = doTestAndIgnoreConflicts { removeParameter(1) }

    fun testJvmOverloadedRemoveNonDefault3() = doTestAndIgnoreConflicts { removeParameter(getNewParametersCount() - 1) }

    fun testJvmOverloadedConstructorSwapParams() = doTestAndIgnoreConflicts { swapParameters(1, 2) }

    fun testDefaultAfterLambda() = doTest { swapParameters(0, 1) }

    fun testRemoveDefaultParameterBeforeLambda() = doTest { removeParameter(1) }

    fun testAddParameterKeepFormat() = doTest {
        val psiFactory = KtPsiFactory(project)
        val defaultValue1 = psiFactory.createExpression("4")
        val defaultValue2 = psiFactory.createExpression("5")
        addParameter(
            KotlinParameterInfo(
                callableDescriptor = originalBaseFunctionDescriptor,
                name = "d",
                originalTypeInfo = kotlinIntType,
                defaultValueForCall = defaultValue1,
            ), 2
        )
        addParameter(
            KotlinParameterInfo(
                callableDescriptor = originalBaseFunctionDescriptor,
                name = "e",
                originalTypeInfo = kotlinIntType,
                defaultValueForCall = defaultValue2,
            )
        )
    }

    fun testRemoveParameterKeepFormat1() = doTest { removeParameter(0) }

    fun testRemoveParameterKeepFormat2() = doTest { removeParameter(1) }

    fun testRemoveParameterKeepFormat3() = doTest { removeParameter(2) }

    fun testSwapParametersKeepFormat() = doTest { swapParameters(0, 2) }

    fun testSetErrorReturnType() = doTest { newReturnTypeInfo = KotlinTypeInfo(true, null, "XYZ") }

    fun testSetErrorReceiverType() = doTest { receiverParameterInfo!!.currentTypeInfo = KotlinTypeInfo(true, null, "XYZ") }

    fun testSetErrorParameterType() = doTest { newParameters[1].currentTypeInfo = KotlinTypeInfo(true, null, "XYZ") }

    fun testSwapDataClassParameters() = doTest {
        swapParameters(0, 2)
        swapParameters(1, 2)
    }

    fun testAddDataClassParameter() = doTest {
        addParameter(
            KotlinParameterInfo(
                callableDescriptor = originalBaseFunctionDescriptor,
                name = "c",
                originalTypeInfo = kotlinIntType,
                defaultValueForCall = KtPsiFactory(project).createExpression("3"),
                valOrVar = KotlinValVar.Val,
            ),
            1,
        )
    }

    fun testRemoveDataClassParameter() = doTest { removeParameter(1) }

    fun testRemoveAllOriginalDataClassParameters() = doTest {
        val psiFactory = KtPsiFactory(project)

        swapParameters(1, 2)
        setNewParameter(
            0,
            KotlinParameterInfo(
                callableDescriptor = originalBaseFunctionDescriptor,
                name = "d",
                originalTypeInfo = kotlinIntType,
                defaultValueForCall = psiFactory.createExpression("4"),
                valOrVar = KotlinValVar.Val,
            )
        )

        setNewParameter(
            2,
            KotlinParameterInfo(
                callableDescriptor = originalBaseFunctionDescriptor,
                name = "e",
                originalTypeInfo = kotlinIntType,
                defaultValueForCall = psiFactory.createExpression("5"),
                valOrVar = KotlinValVar.Val,
            )
        )
    }

    fun testImplicitReceiverInRecursiveCall() = doTest {
        receiverParameterInfo = null
        newParameters[0].name = "a"
    }

    fun testReceiverInSafeCall() = doTestConflict { receiverParameterInfo = null }

    fun testRemoveParameterKeepOtherComments() = doTest { removeParameter(1) }

    fun testReturnTypeViaCodeFragment() = doTest {
        newName = "bar"
        newReturnTypeInfo = resolveType("A<T, U>", isCovariant = true, forPreview = true)
    }

    fun testChangeReturnTypeToNonUnit() = doTest {
        newReturnTypeInfo = kotlinIntType
    }

    fun testInvokeConventionRemoveParameter() = doTest { removeParameter(0) }

    fun testInvokeConventionAddParameter() = doTest {
        addParameter(
            KotlinParameterInfo(
                callableDescriptor = originalBaseFunctionDescriptor,
                name = "b",
                originalTypeInfo = KotlinTypeInfo(false, BUILT_INS.booleanType),
                defaultValueForCall = KtPsiFactory(project).createExpression("false"),
            )
        )
    }

    fun testInvokeConventionSwapParameters() = doTest { swapParameters(0, 1) }

    fun testInvokeConventionParameterToReceiver() = doTestConflict { receiverParameterInfo = newParameters[0] }

    fun testInvokeConventionReceiverToParameter() = doTest { receiverParameterInfo = null }

    fun testInvokeConventionRenameToFoo() = doTest { newName = "foo" }

    fun testInvokeConventionRenameToGet() = doTest { newName = "get" }

    fun testGetConventionRemoveParameter() = doTest { removeParameter(0) }

    fun testGetConventionAddParameter() = doTest {
        addParameter(
            KotlinParameterInfo(
                callableDescriptor = originalBaseFunctionDescriptor,
                originalIndex = -1,
                name = "b",
                originalTypeInfo = KotlinTypeInfo(false, BUILT_INS.booleanType),
                defaultValueForCall = KtPsiFactory(project).createExpression("false"),
            )
        )
    }

    fun testGetConventionSwapParameters() = doTest { swapParameters(0, 1) }

    fun testGetConventionParameterToReceiver() = doTestConflict { receiverParameterInfo = newParameters[0] }

    fun testGetConventionReceiverToParameter() = doTest { receiverParameterInfo = null }

    fun testGetConventionRenameToFoo() = doTest { newName = "foo" }

    fun testGetConventionRenameToInvoke() = doTest { newName = "invoke" }

    fun testKotlinOverridingJavaWithDifferentParamName() = doJavaTest {
        newParameters.add(ParameterInfoImpl(-1, "n", PsiType.INT))
    }

    fun testAddParameterAfterLambdaParameter() = doTest {
        addParameter(createKotlinIntParameter(defaultValueForCall = KtPsiFactory(project).createExpression("0")))
    }

    fun testRemoveLambdaParameter2() = doTest { removeParameter(0) }

    fun testNewParamValueRefsOtherParam() = doTest {
        val parameterInfo = KotlinParameterInfo(originalBaseFunctionDescriptor, -1, "p2", kotlinIntType)
        val codeFragment = KtPsiFactory(project).createExpressionCodeFragment("p1 * p1", context)
        parameterInfo.defaultValueForCall = codeFragment.getContentElement()!!
        addParameter(parameterInfo)
    }

    fun testMoveLambdaParameterToLast() = doTest { swapParameters(0, 1) }
}

fun createChangeInfo(
    project: Project,
    editor: Editor?,
    callableDescriptor: CallableDescriptor,
    configuration: KotlinChangeSignatureConfiguration,
    defaultValueContext: PsiElement
): KotlinChangeInfo? {
    val kotlinChangeSignature = KotlinChangeSignature(project, editor, callableDescriptor, configuration, defaultValueContext, null)
    val declarations = callableDescriptor.safeAs<CallableMemberDescriptor>()?.getDeepestSuperDeclarations() ?: listOf(callableDescriptor)
    val adjustedDescriptor = kotlinChangeSignature.adjustDescriptor(declarations) ?: return null
    val processor = kotlinChangeSignature.createSilentRefactoringProcessor(adjustedDescriptor) as KotlinChangeSignatureProcessor
    return processor.ktChangeInfo.also { it.checkUsedParameters = true }
}
