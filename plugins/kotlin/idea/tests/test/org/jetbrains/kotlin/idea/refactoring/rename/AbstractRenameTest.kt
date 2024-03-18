// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.google.gson.JsonObject
import com.google.gson.JsonParser.parseString
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.psi.Property
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException
import com.intellij.refactoring.rename.*
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.refactoring.util.CommonRefactoringUtil.RefactoringErrorHintException
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import org.jetbrains.kotlin.asJava.finder.KtLightPackage
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.util.allScope
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.idea.jsonUtils.getNullableString
import org.jetbrains.kotlin.idea.base.util.getString
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.*
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.junit.Assert
import java.io.File

enum class RenameType {
    JAVA_CLASS,
    JAVA_METHOD,
    KOTLIN_CLASS,
    KOTLIN_FUNCTION,
    KOTLIN_PROPERTY,

    /**
     * Kotlin enum entries are classes in FE10 (represented as `LazyClassDescriptor`s), but variables/callables in FIR (represented as
     * `FirEnumEntry`, which is a `FirVariable`). Hence, they have to be treated separately from classes and callables in test data.
     */
    KOTLIN_ENUM_ENTRY,

    KOTLIN_PACKAGE,
    MARKED_ELEMENT,
    FILE,
    BUNDLE_PROPERTY,
    AUTO_DETECT
}

abstract class AbstractRenameTest : KotlinLightCodeInsightFixtureTestCase() {
    inner class TestContext(
        val testFile: File,
        val project: Project = getProject(),
        val javaFacade: JavaPsiFacade = myFixture.javaFacade,
        val module: Module = myFixture.module
    )

    override fun getProjectDescriptor(): LightProjectDescriptor {
        val testConfigurationFile = dataFile()
        val renameObject = loadTestConfiguration(testConfigurationFile)
        val withRuntime = renameObject.getNullableString("withRuntime")
        val libraryInfos = renameObject.getAsJsonArray("libraries")?.map { it.asString!! }
        if (libraryInfos != null) {
            val jarPaths = listOf(TestKotlinArtifacts.kotlinStdlib) + libraryInfos.map { libraryInfo ->
                if ("@" in libraryInfo) {
                    File(PlatformTestUtil.getCommunityPath(), libraryInfo.substringAfter("@"))
                }
                else {
                    ConfigLibraryUtil.ATTACHABLE_LIBRARIES[libraryInfo]
                }
            }
            return KotlinWithJdkAndRuntimeLightProjectDescriptor(jarPaths, listOf(TestKotlinArtifacts.kotlinStdlibSources))
        }

        if (withRuntime != null) {
            return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
        }
        return getDefaultProjectDescriptor()
    }

    open fun doTest(path: String) {
        val testFile = File(path)
        val renameObject = loadTestConfiguration(testFile)

        val testIsEnabledInK2 = renameObject.get(if (isFirPlugin) "enabledInK2" else "enabledInK1")?.asBoolean ?: if (isFirPlugin) error("`enabledInK2` has to be specified explicitly") else true

        val result = runCatching {
            val renameTypeStr = renameObject.getString("type")

            val hintsDirective = setOfNotNull(renameObject.getNullableString("hint"), renameObject.getNullableString("k1Hint"))

            val fixtureClasses = renameObject.getAsJsonArray("fixtureClasses")?.map { it.asString } ?: emptyList()

            try {
                fixtureClasses.forEach { TestFixtureExtension.loadFixture(it, module) }

                val context = TestContext(testFile)

                when (RenameType.valueOf(renameTypeStr)) {
                    RenameType.JAVA_CLASS -> renameJavaClassTest(renameObject, context)
                    RenameType.JAVA_METHOD -> renameJavaMethodTest(renameObject, context)
                    RenameType.KOTLIN_CLASS -> renameKotlinClassTest(renameObject, context)
                    RenameType.KOTLIN_FUNCTION -> renameKotlinFunctionTest(renameObject, context)
                    RenameType.KOTLIN_PROPERTY -> renameKotlinPropertyTest(renameObject, context)
                    RenameType.KOTLIN_ENUM_ENTRY -> renameKotlinEnumEntryTest(renameObject, context)
                    RenameType.KOTLIN_PACKAGE -> renameKotlinPackageTest(renameObject, context)
                    RenameType.MARKED_ELEMENT -> renameMarkedElement(renameObject, context)
                    RenameType.FILE -> renameFile(renameObject, context)
                    RenameType.BUNDLE_PROPERTY -> renameBundleProperty(renameObject, context)
                    RenameType.AUTO_DETECT -> renameWithAutoDetection(renameObject, context)
                }

                if (hintsDirective.isNotEmpty()) {
                    Assert.fail("""Hint "${hintsDirective.first()}" was expected""")
                }

                if (renameObject["checkErrorsAfter"]?.asBoolean == true) {
                    val psiManager = myFixture.psiManager
                    val visitor = object : VirtualFileVisitor<Any>() {
                        override fun visitFile(file: VirtualFile): Boolean {
                            (psiManager.findFile(file) as? KtFile)?.let { checkForUnexpectedErrors(it) }
                            return true
                        }
                    }

                    for (sourceRoot in ModuleRootManager.getInstance(module).sourceRoots) {
                        VfsUtilCore.visitChildrenRecursively(sourceRoot, visitor)
                    }
                }
            } catch (e: Exception) {
                if (e !is RefactoringErrorHintException && e !is ConflictsInTestsException) throw e

                val hintExceptionUnquoted = StringUtil.unquoteString(e.message!!)
                if (hintsDirective.isNotEmpty()) {
                    Assert.assertTrue("Expected one of $hintsDirective but was $hintExceptionUnquoted",
                                      hintsDirective.contains(hintExceptionUnquoted))
                } else {
                    Assert.fail("""Unexpected "hint: $hintExceptionUnquoted" """)
                }
            } finally {
                fixtureClasses.forEach { TestFixtureExtension.unloadFixture(it) }
            }
        }
        result.fold(
            onSuccess = { require(testIsEnabledInK2) { "This test passes and should be enabled!" } },
            onFailure = { exception -> if (testIsEnabledInK2) throw exception }
        )
    }

    protected open fun checkForUnexpectedErrors(ktFile: KtFile) {
        DirectiveBasedActionUtils.checkForUnexpectedErrors(ktFile)
    }

    protected open fun configExtra(rootDir: VirtualFile, renameParamsObject: JsonObject) {

    }

    private fun renameMarkedElement(renameParamsObject: JsonObject, context: TestContext) {
        val mainFilePath = renameParamsObject.getString("mainFile")

        doTestCommittingDocuments(context) { rootDir ->
            configExtra(rootDir, renameParamsObject)
            val psiFile = myFixture.configureFromTempProjectFile(mainFilePath)

            doRenameMarkedElement(renameParamsObject, psiFile)
        }
    }

    private fun renameJavaClassTest(renameParamsObject: JsonObject, context: TestContext) {
        val classFQN = renameParamsObject.getString("classId").toClassId().asSingleFqName().asString()
        val newName = renameParamsObject.getString("newName")

        doTestCommittingDocuments(context) {
            val aClass = context.javaFacade.findClass(classFQN, context.project.allScope())!!
            val substitution = RenamePsiElementProcessor.forElement(aClass).substituteElementToRename(aClass, null)

            runRenameProcessor(context.project, newName, substitution, renameParamsObject, true, true)
        }
    }

    private fun renameJavaMethodTest(renameParamsObject: JsonObject, context: TestContext) {
        val classFQN = renameParamsObject.getString("classId").toClassId().asSingleFqName().asString()
        val methodSignature = renameParamsObject.getString("methodSignature")
        val newName = renameParamsObject.getString("newName")

        doTestCommittingDocuments(context) {
            val aClass = context.javaFacade.findClass(classFQN, GlobalSearchScope.moduleScope(context.module))!!

            val methodText = context.javaFacade.elementFactory.createMethodFromText("$methodSignature{}", null)
            val method = aClass.findMethodBySignature(methodText, false)

            if (method == null) throw IllegalStateException("Method with signature '$methodSignature' wasn't found in class $classFQN")

            val substitution = RenamePsiElementProcessor.forElement(method).substituteElementToRename(method, null)
            runRenameProcessor(context.project, newName, substitution, renameParamsObject, false, false)
        }
    }

    protected sealed interface KotlinTarget {
        enum class CallableType {
            FUNCTION, PROPERTY
        }

        class Callable(val callableId: CallableId, val type: CallableType) : KotlinTarget

        class Classifier(val classId: ClassId) : KotlinTarget

        class EnumEntry(val enumClassId: ClassId, val enumEntryName: Name) : KotlinTarget {
            val classId: ClassId get() = enumClassId.createNestedClassId(enumEntryName)
            val callableId: CallableId get() = CallableId(enumClassId, enumEntryName)
        }

        companion object {
            fun fromJson(renameParamsObject: JsonObject): KotlinTarget {
                val packageFqn = renameParamsObject.getNullableString("packageFqn")?.let(::FqName)
                val classId = renameParamsObject.getNullableString("classId")?.toClassId()

                if (classId != null && packageFqn != null) {
                    throw AssertionError("Both classId and packageFqn are defined. Where should I search: in class or in package?")
                } else if (classId == null && packageFqn == null) {
                    throw AssertionError("Define classId or packageFqn")
                }

                val enumEntryName = renameParamsObject.getNullableString("enumEntryName")?.let(Name::identifier)
                if (enumEntryName != null) {
                    return EnumEntry(classId!!, enumEntryName)
                }

                val oldName = renameParamsObject.getNullableString("oldName")?.let(Name::identifier)

                if (oldName == null) return Classifier(classId!!)

                val callableId = if (packageFqn != null) {
                    CallableId(packageFqn, oldName)
                } else {
                    CallableId(classId!!, oldName)
                }

                val callableType = when (val renameType = RenameType.valueOf(renameParamsObject.getString("type"))) {
                    RenameType.KOTLIN_FUNCTION -> CallableType.FUNCTION
                    RenameType.KOTLIN_PROPERTY -> CallableType.PROPERTY
                    else -> error("Unexpected rename type ${renameType}")
                }

                return Callable(callableId, callableType)
            }
        }
    }

    private fun renameKotlinFunctionTest(renameParamsObject: JsonObject, context: TestContext) {
        val target = KotlinTarget.fromJson(renameParamsObject)
        require(target is KotlinTarget.Callable && target.type == KotlinTarget.CallableType.FUNCTION)

        renameKotlinTarget(target, renameParamsObject, context)
    }

    private fun renameKotlinPropertyTest(renameParamsObject: JsonObject, context: TestContext) {
        val target = KotlinTarget.fromJson(renameParamsObject)
        require(target is KotlinTarget.Callable && target.type == KotlinTarget.CallableType.PROPERTY)

        renameKotlinTarget(target, renameParamsObject, context)
    }

    private fun renameKotlinClassTest(renameParamsObject: JsonObject, context: TestContext) {
        val target = KotlinTarget.fromJson(renameParamsObject)
        require(target is KotlinTarget.Classifier)

        renameKotlinTarget(target, renameParamsObject, context)
    }

    private fun renameKotlinEnumEntryTest(renameParamsObject: JsonObject, context: TestContext) {
        val target = KotlinTarget.fromJson(renameParamsObject)
        require(target is KotlinTarget.EnumEntry)

        renameKotlinTarget(target, renameParamsObject, context)
    }

    private fun renameKotlinPackageTest(renameParamsObject: JsonObject, context: TestContext) {
        val fqn = FqNameUnsafe(renameParamsObject.getString("fqn")).toSafe()
        val newName = renameParamsObject.getString("newName")
        val mainFilePath = renameParamsObject.getNullableString("mainFile") ?: "${getTestDirName(false)}.kt"

        doTestCommittingDocuments(context) {
            val mainFile = myFixture.configureFromTempProjectFile(mainFilePath) as KtFile

            val fileFqn = mainFile.packageFqName
            Assert.assertTrue("File '${mainFilePath}' should have package containing ${fqn}", fileFqn.isSubpackageOf(fqn))

            val packageDirective = mainFile.packageDirective!!
            val packageSegment = packageDirective.packageNames[fqn.pathSegments().size - 1]
            val segmentReference = packageSegment.mainReference

            val psiElement = segmentReference.resolve() ?: error("unable to resolve '${segmentReference.element.text}' from $packageDirective '${packageDirective.text}'")

            val substitution = RenamePsiElementProcessor.forElement(psiElement).substituteElementToRename(psiElement, null)
            runRenameProcessor(context.project, newName, substitution, renameParamsObject, true, true)
        }
    }

    private fun renameFile(renameParamsObject: JsonObject, context: TestContext) {
        val file = renameParamsObject.getString("file")
        val newName = renameParamsObject.getString("newName")

        doTestCommittingDocuments(context) {
            val psiFile = myFixture.configureFromTempProjectFile(file)

            runRenameProcessor(context.project, newName, psiFile, renameParamsObject, true, true)
        }
    }

    private fun renameBundleProperty(renameParamsObject: JsonObject, context: TestContext) {
        val file = renameParamsObject.getString("file")
        val oldName = renameParamsObject.getString("oldName")
        val newName = renameParamsObject.getString("newName")

        doTestCommittingDocuments(context) {
            val mainFile = myFixture.configureFromTempProjectFile(file) as PropertiesFile
            val property = mainFile.findPropertyByKey(oldName) as Property

            runRenameProcessor(context.project, newName, property, renameParamsObject, true, true)
        }
    }

    private fun renameKotlinTarget(
        target: KotlinTarget,
        renameParamsObject: JsonObject,
        context: TestContext,
    ) {
        val newName = renameParamsObject.getString("newName")
        val mainFilePath = renameParamsObject.getNullableString("mainFile") ?: "${getTestDirName(false)}.kt"

        doTestCommittingDocuments(context) {
            val ktFile = myFixture.configureFromTempProjectFile(mainFilePath) as KtFile

            val psiElement = findPsiDeclarationToRename(ktFile, target)

            // The Java processor always chooses renaming the base element when running in unit test mode,
            // so if we want to rename only the inherited element, we need to skip the substitutor.
            val skipSubstitute = renameParamsObject["skipSubstitute"]?.asBoolean ?: false
            val substitution = if (skipSubstitute)
                psiElement
            else
                RenamePsiElementProcessor.forElement(psiElement).substituteElementToRename(psiElement, null)

            runRenameProcessor(context.project, newName, substitution, renameParamsObject, true, true)
            PsiTestUtil.checkFileStructure(ktFile)
        }
    }

    protected open fun findPsiDeclarationToRename(
        contextFile: KtFile,
        target: KotlinTarget
    ): PsiElement {
        val module = contextFile.analyzeWithAllCompilerChecks().moduleDescriptor

        val descriptor = when (target) {
            is KotlinTarget.Classifier -> module.findClassAcrossModuleDependencies(target.classId)!!

            is KotlinTarget.Callable -> {
                val callableId = target.callableId

                val targetScope = callableId.classId
                    ?.let { classId -> module.findClassAcrossModuleDependencies(classId)!!.defaultType.memberScope }
                    ?: module.getPackage(callableId.packageName).memberScope

                when (target.type) {
                    KotlinTarget.CallableType.FUNCTION -> targetScope.getContributedFunctions(
                        callableId.callableName,
                        NoLookupLocation.FROM_TEST
                    ).first()

                    KotlinTarget.CallableType.PROPERTY -> targetScope.getContributedVariables(
                        callableId.callableName,
                        NoLookupLocation.FROM_TEST
                    ).first()
                }
            }

            is KotlinTarget.EnumEntry -> module.findClassAcrossModuleDependencies(target.classId)!!
        }

        return DescriptorToSourceUtils.descriptorToDeclaration(descriptor)!!
    }

    private fun renameWithAutoDetection(renameParamsObject: JsonObject, context: TestContext) {
        val mainFilePath = renameParamsObject.getString("mainFile")
        val newName = renameParamsObject.getString("newName")

        doTestCommittingDocuments(context) { rootDir ->
            configExtra(rootDir, renameParamsObject)

            val psiFile = myFixture.configureFromTempProjectFile(mainFilePath)

            val doc = PsiDocumentManager.getInstance(project).getDocument(psiFile)!!
            val marker = doc.extractMarkerOffset(project, "/*rename*/")
            assert(marker != -1)

            editor.caretModel.moveToOffset(marker)
            val currentCaret = editor.caretModel.currentCaret
            val dataContext = createTextEditorBasedDataContext(project, editor, currentCaret) {
                add(PsiElementRenameHandler.DEFAULT_NAME, newName)
            }

            var handler = RenameHandlerRegistry.getInstance().getRenameHandler(dataContext) ?: return@doTestCommittingDocuments
            Assert.assertTrue(handler.isAvailableOnDataContext(dataContext))
            if (handler is KotlinRenameDispatcherHandler) {
                handler = handler.getRenameHandler(dataContext)!!
            }

            if (handler is VariableInplaceRenameHandler && renameParamsObject.get("forceInlineRename")?.asBoolean != false) {
                val elementToRename = psiFile.findElementAt(currentCaret.offset)!!.getNonStrictParentOfType<PsiNamedElement>()!!
                CodeInsightTestUtil.doInlineRename(handler, newName, editor, elementToRename)
            } else {
                handler.invoke(project, editor, psiFile, dataContext)
            }
        }
    }

    protected fun getTestDirName(lowercaseFirstLetter: Boolean): String {
        val testName = getTestName(lowercaseFirstLetter)
        return testName.substring(0, testName.indexOf('_'))
    }

    protected fun doTestCommittingDocuments(context: TestContext, action: (VirtualFile) -> Unit) {
        val beforeDir = context.testFile.parentFile.name + "/before"
        val beforeVFile = myFixture.copyDirectoryToProject(beforeDir, "")
        PsiDocumentManager.getInstance(myFixture.project).commitAllDocuments()

        val afterDir = File(context.testFile.parentFile, "after")
        action(beforeVFile)

        PsiDocumentManager.getInstance(project).commitAllDocuments()
        FileDocumentManager.getInstance().saveAllDocuments()

        val afterVFile = LocalFileSystem.getInstance().findFileByIoFile(afterDir)?.apply {
            UsefulTestCase.refreshRecursively(this)
        } ?: error("`after` directory not found")

        PlatformTestUtil.assertDirectoriesEqual(afterVFile, beforeVFile)
    }
}

private fun String.toClassId(): ClassId = ClassId.fromString(this)

fun loadTestConfiguration(testFile: File): JsonObject {
    val fileText = FileUtil.loadFile(testFile, true)

    return parseString(fileText) as JsonObject
}

fun runRenameProcessor(
    project: Project,
    newName: String,
    substitution: PsiElement?,
    renameParamsObject: JsonObject,
    isSearchInComments: Boolean,
    isSearchTextOccurrences: Boolean
) {
    if (substitution == null) return

    fun createProcessor(): BaseRefactoringProcessor {
        if (substitution is PsiPackage && substitution !is KtLightPackage) {
            val oldName = substitution.qualifiedName
            if (StringUtil.getPackageName(oldName) != StringUtil.getPackageName(newName)) {
                return RenamePsiPackageProcessor.createRenameMoveProcessor(
                    newName,
                    substitution,
                    isSearchInComments,
                    isSearchTextOccurrences
                )
            }
        }

        return RenameProcessor(project, substitution, newName, isSearchInComments, isSearchTextOccurrences)
    }

    val processor = createProcessor()

    if (renameParamsObject["overloadRenamer.onlyPrimaryElement"]?.asBoolean == true) {
        with(AutomaticOverloadsRenamer) { substitution.elementFilter = { false } }
    }
    if (processor is RenameProcessor) {
        @Suppress("DEPRECATION")
        Extensions.getExtensions(AutomaticRenamerFactory.EP_NAME).forEach { processor.addRenamerFactory(it) }
    }
    processor.run()
}

fun doRenameMarkedElement(renameParamsObject: JsonObject, psiFile: PsiFile) {
    val project = psiFile.project
    val newName = renameParamsObject.getString("newName")

    val doc = PsiDocumentManager.getInstance(project).getDocument(psiFile)!!
    val marker = doc.extractMarkerOffset(project, "/*rename*/")
    assert(marker != -1)

    val editorFactory = EditorFactory.getInstance()
    var editor = editorFactory.getEditors(doc).firstOrNull()
    var shouldReleaseEditor = false
    if (editor == null) {
        editor = editorFactory.createEditor(doc)
        shouldReleaseEditor = true
    }

    try {
        val isByRef = renameParamsObject["byRef"]?.asBoolean ?: false
        val isInjected = renameParamsObject["injected"]?.asBoolean ?: false
        var currentEditor = editor!!
        var currentFile: PsiFile = psiFile
        if (isByRef || isInjected) {
            currentEditor.caretModel.moveToOffset(marker)
            if (isInjected) {
                currentFile = InjectedLanguageUtil.findInjectedPsiNoCommit(psiFile, marker)!!
                currentEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(editor, currentFile)
            }
        }
        val toRename = if (isByRef) {
            TargetElementUtil.findTargetElement(currentEditor, TargetElementUtil.getInstance().allAccepted)!!
        } else {
            currentFile.findElementAt(marker)!!.getNonStrictParentOfType<PsiNamedElement>()!!
        }

        val substitution = RenamePsiElementProcessor.forElement(toRename).substituteElementToRename(toRename, null)

        val searchInComments = renameParamsObject["searchInComments"]?.asBoolean ?: true
        val searchInTextOccurrences = renameParamsObject["searchInTextOccurrences"]?.asBoolean ?: true
        runRenameProcessor(project, newName, substitution, renameParamsObject, searchInComments, searchInTextOccurrences)
    } finally {
        if (shouldReleaseEditor) {
            editorFactory.releaseEditor(editor!!)
        }
    }
}
