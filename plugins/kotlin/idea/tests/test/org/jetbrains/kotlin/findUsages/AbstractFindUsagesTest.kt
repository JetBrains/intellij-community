// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.findUsages

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.find.FindManager
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.find.findUsages.JavaFindUsagesHandler
import com.intellij.find.findUsages.JavaFindUsagesHandlerFactory
import com.intellij.find.impl.FindManagerImpl
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.psi.Property
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.UsefulTestCase
import com.intellij.usageView.UsageInfo
import com.intellij.usages.TextChunk
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider
import com.intellij.usages.rules.ImportFilteringRule
import com.intellij.usages.rules.UsageGroupingRule
import com.intellij.util.CommonProcessors
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.executeOnPooledThreadInReadAction
import org.jetbrains.kotlin.findUsages.AbstractFindUsagesTest.Companion.FindUsageTestType
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinFunctionFindUsagesOptions
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinPropertyFindUsagesOptions
import org.jetbrains.kotlin.idea.base.searching.usages.handlers.KotlinFindMemberUsagesHandler
import org.jetbrains.kotlin.idea.base.util.*
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.caches.resolve.resolveToParameterDescriptorIfAny
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.findUsages.KotlinFindUsagesSupport
import org.jetbrains.kotlin.idea.search.usagesSearch.ExpressionsOfTypeProcessor
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.assertEqualsToFile
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import java.io.File


abstract class AbstractFindUsagesWithDisableComponentSearchTest : AbstractFindUsagesTest() {

    override fun <T : PsiElement> doTest(path: String) {
        val oldValue = KotlinFindMemberUsagesHandler.forceDisableComponentAndDestructionSearch
        try {
            KotlinFindMemberUsagesHandler.forceDisableComponentAndDestructionSearch = true
            super.doTest<T>(path)
        } finally {
            KotlinFindMemberUsagesHandler.forceDisableComponentAndDestructionSearch = oldValue
        }
    }

    override val prefixForResults = "DisabledComponents."
}

abstract class AbstractKotlinScriptFindUsagesTest : AbstractFindUsagesTest() {
    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceWithScriptRuntime()
}

abstract class AbstractFindUsagesTest : KotlinLightCodeInsightFixtureTestCase() {

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceNoSources()

    // used in Spring tests (outside main project!)
    protected open fun extraConfig(path: String) {
    }

    protected open val prefixForResults = ""

    protected open val ignoreLog = false

    protected open fun <T : PsiElement> doTest(path: String): Unit = doFindUsageTest<T>(
        path,
        this::extraConfig,
        KotlinFindUsageConfigurator.fromFixture(myFixture),
        if (isFirPlugin) FindUsageTestType.FIR else FindUsageTestType.DEFAULT,
        prefixForResults,
        ignoreLog,
    )

    companion object {
        enum class FindUsageTestType(val isFir: Boolean, val isCri: Boolean) {
            DEFAULT(isFir = false, isCri = false),
            FIR(isFir = true, isCri = false),

            CRI(isFir = false, isCri = true),
            FIR_CRI(isFir = true, isCri = true),
        }

        fun <T : PsiElement> doFindUsageTest(
            path: String,
            extraConfig: (path: String) -> Unit = { },
            configurator: KotlinFindUsageConfigurator,
            testType: FindUsageTestType = FindUsageTestType.DEFAULT,
            prefixForResults: String = "",
            ignoreLog: Boolean,
            executionWrapper: (findUsageTest: (FindUsageTestType) -> Unit) -> Unit = { it(testType) }
        ) {
            val mainFile = File(path)
            val mainFileName = mainFile.name
            val mainFileText = FileUtil.loadFile(mainFile, true)
            val prefix = mainFileName.substringBefore(".") + "."

            val isPropertiesFile = FileUtilRt.getExtension(path) == "properties"

            InTextDirectivesUtils.findStringWithPrefixes(mainFileText, "// IGNORE: ")?.let {
                println("test $mainFileName is ignored")
                return
            }

            if (testType.isCri) {
                if (InTextDirectivesUtils.isDirectiveDefined(mainFileText, "// CRI_IGNORE")) {
                    println("test $mainFileName is ignored (${testType.name})")
                    return
                } else if (InTextDirectivesUtils.isDirectiveDefined(mainFileText, DirectiveBasedActionUtils.DISABLE_ERRORS_DIRECTIVE)) {
                    return
                }
            }

            //        if (isFirPlugin) {
            //            InTextDirectivesUtils.findStringWithPrefixes(mainFileText, "// FIR_IGNORE")?.let {
            //                return
            //            }
            //        }

            val isFindFileUsages = InTextDirectivesUtils.isDirectiveDefined(mainFileText, "## FIND_FILE_USAGES")

            @Suppress("UNCHECKED_CAST")
            val caretElementClass = (if (!isPropertiesFile) {
                val caretElementClassNames = InTextDirectivesUtils.findLinesWithPrefixesRemoved(mainFileText, "// PSI_ELEMENT: ")
                Class.forName(caretElementClassNames.single())
            } else if (isFindFileUsages) {
                PropertiesFile::class.java
            } else {
                Property::class.java
            }) as Class<T>

            val fixtureClasses = InTextDirectivesUtils.findListWithPrefixes(mainFileText, "// FIXTURE_CLASS: ")
            for (fixtureClass in fixtureClasses) {
                TestFixtureExtension.loadFixture(fixtureClass, configurator.module)
            }

            try {
                extraConfig(path)

                val parser = OptionsParser.getParserByPsiElementClass(caretElementClass)

                val rootPath = path.substringBeforeLast(File.separator) + File.separator

                val rootDir = File(rootPath)
                val extraFiles = rootDir.listFiles { _, name ->
                    if (!name.startsWith(prefix) || name == mainFileName) return@listFiles false

                    val ext = FileUtilRt.getExtension(name)
                    ext in SUPPORTED_EXTENSIONS && !name.endsWith(".results.txt")
                }.orEmpty()

                configurator.configureByFiles(listOf(mainFileName) + extraFiles.map(File::getName))
                if ((configurator.file as? KtFile)?.isScript() == true) {
                    ScriptConfigurationManager.updateScriptDependenciesSynchronously(configurator.file)
                }

                val javaNamesMap: Map<String, String> = FileTypeIndex.getFiles(
                    JavaFileType.INSTANCE,
                    configurator.project.projectScope(),
                ).mapNotNull { vFile ->
                    val psiFile = vFile.toPsiFile(configurator.project) as PsiJavaFile
                    val className = psiFile.classes.firstOrNull { it.hasModifier(JvmModifier.PUBLIC) }?.name ?: return@mapNotNull null
                    val newFileName = "$className.java"
                    val oldFileName = vFile.name
                    configurator.renameElement(psiFile, newFileName)
                    newFileName to oldFileName
                }.associate { it }

                if (testType == FindUsageTestType.DEFAULT) {
                    (configurator.file as? KtFile)?.let { ktFile ->
                        val diagnosticsProvider: (KtFile) -> Diagnostics = { it.analyzeWithAllCompilerChecks().bindingContext.diagnostics }
                        DirectiveBasedActionUtils.checkForUnexpectedWarnings(ktFile, diagnosticsProvider = diagnosticsProvider)
                        DirectiveBasedActionUtils.checkForUnexpectedErrors(ktFile, diagnosticsProvider = diagnosticsProvider)
                    }
                }

                val caretElement = when {
                    InTextDirectivesUtils.isDirectiveDefined(mainFileText, "// FIND_BY_REF") -> {
                        executeOnPooledThreadInReadAction {
                            TargetElementUtil.findTargetElement(
                                configurator.editor,
                                TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED or TargetElementUtil.getInstance().referenceSearchFlags,
                            )
                        }
                    }

                    isFindFileUsages -> configurator.file
                    else -> configurator.elementAtCaret
                }

                val psiElementAsTitle = when(caretElement) {
                    is KtClass, is KtProperty, is KtParameter ->
                        KotlinFindUsagesSupport.tryRenderDeclarationCompactStyle(caretElement as KtDeclaration)
                    is KtFunction -> caretElement.toLightMethods().firstOrNull()?.let { KotlinFindUsagesSupport.formatJavaOrLightMethod(it) }
                    is PsiMethod ->
                        if (caretElement.unwrapped is KtDeclaration) {
                            KotlinFindUsagesSupport.formatJavaOrLightMethod(caretElement)
                        } else {
                            null
                        }
                    else -> null
                }

                val expectedPsiElementAsTitle = InTextDirectivesUtils.findStringWithPrefixes(mainFileText, "// PSI_ELEMENT_AS_TITLE:")
                assertEqualsToFile(
                  mainFile,
                  mainFileText.replace("// PSI_ELEMENT_AS_TITLE: \"$expectedPsiElementAsTitle\"\n",
                                       if (psiElementAsTitle != null) "// PSI_ELEMENT_AS_TITLE: \"$psiElementAsTitle\"\n" else "")
                )

                UsefulTestCase.assertInstanceOf(caretElement!!, caretElementClass)

                val containingFile = caretElement.containingFile
                val project = configurator.project
                val isLibraryElement = containingFile != null && RootKindFilter.libraryFiles.matches(containingFile)

                val options = parser?.parse(mainFileText, project)

                // Ensure that search by sources (if present) and decompiled declarations gives the same results
                val prefixForCheck = prefix + prefixForResults
                var wasExecuted = false
                executionWrapper { executionTestType ->
                    wasExecuted = true
                    if (isLibraryElement) {
                        val originalElement = caretElement.originalElement
                        findUsagesAndCheckResults(
                            mainFileText,
                            prefixForCheck,
                            rootPath,
                            originalElement,
                            options,
                            project,
                            alwaysAppendFileName = false,
                            testType = executionTestType,
                            javaNamesMap = javaNamesMap,
                            ignoreLog = ignoreLog
                        )

                        val navigationElement = caretElement.navigationElement
                        if (navigationElement !== originalElement) {
                            findUsagesAndCheckResults(
                                mainFileText,
                                prefixForCheck,
                                rootPath,
                                navigationElement,
                                options,
                                project,
                                alwaysAppendFileName = false,
                                testType = executionTestType,
                                javaNamesMap = javaNamesMap,
                                ignoreLog = ignoreLog
                            )
                        }
                    } else {
                        findUsagesAndCheckResults(
                            mainFileText,
                            prefixForCheck,
                            rootPath,
                            caretElement,
                            options,
                            project,
                            alwaysAppendFileName = false,
                            testType = executionTestType,
                            javaNamesMap = javaNamesMap,
                            ignoreLog = ignoreLog
                        )
                    }
                }

                assertTrue("'executionWrapper' must call findUsageTest", wasExecuted)
            } finally {
                fixtureClasses.forEach { TestFixtureExtension.unloadFixture(it) }
            }
        }

        private val SUPPORTED_EXTENSIONS = setOf("kt", "kts", "java", "xml", "properties", "txt", "groovy")

        internal fun getUsageAdapters(
            filters: Collection<ImportFilteringRule>,
            usageInfos: Collection<UsageInfo>
        ): Collection<UsageInfo2UsageAdapter> = usageInfos.map(::UsageInfo2UsageAdapter).filter { usageAdapter ->
            filters.all { it.isVisible(usageAdapter) }
        }

        val KtDeclaration.descriptor: DeclarationDescriptor?
            get() = if (this is KtParameter) this.resolveToParameterDescriptorIfAny(BodyResolveMode.FULL) else this.resolveToDescriptorIfAny(BodyResolveMode.FULL)

        internal fun getUsageType(element: PsiElement?): UsageType? {
            if (element == null) return null

            if (element.getNonStrictParentOfType<PsiComment>() != null) {
                return UsageType.COMMENT_USAGE
            }

            return UsageTypeProvider.EP_NAME.extensionList.firstNotNullOfOrNull { it.getUsageType(element) } ?: UsageType.UNCLASSIFIED
        }

        internal fun <T> instantiateClasses(mainFileText: String, directive: String): Collection<T> {
            val filteringRuleClassNames = InTextDirectivesUtils.findLinesWithPrefixesRemoved(mainFileText, directive)
            return filteringRuleClassNames.map {
                @Suppress("UNCHECKED_CAST")
                (Class.forName(it).getDeclaredConstructor().newInstance() as T)
            }
        }
    }
}

internal fun <T : PsiElement> findUsagesAndCheckResults(
    mainFileText: String,
    prefix: String,
    rootPath: String,
    caretElement: T,
    options: FindUsagesOptions?,
    project: Project,
    alwaysAppendFileName: Boolean = false,
    testType: FindUsageTestType = FindUsageTestType.DEFAULT,
    javaNamesMap: Map<String, String>? = null,
    ignoreLog: Boolean = false
) {
    val highlightingMode = InTextDirectivesUtils.isDirectiveDefined(mainFileText, "// HIGHLIGHTING")

    var log: String? = null
    val logList = LinkedHashSet<String>()
    val usageInfos = try {
        if (ExpressionsOfTypeProcessor.mode !== ExpressionsOfTypeProcessor.Mode.ALWAYS_PLAIN) {
            ExpressionsOfTypeProcessor.testLog = logList
        }

        if (InTextDirectivesUtils.isDirectiveDefined(mainFileText, "// PLAIN_WHEN_NEEDED")) {
            ExpressionsOfTypeProcessor.mode = ExpressionsOfTypeProcessor.Mode.PLAIN_WHEN_NEEDED
        }

        val searchSuperDeclaration =
            InTextDirectivesUtils.findLinesWithPrefixesRemoved(mainFileText, "$CHECK_SUPER_METHODS_YES_NO_DIALOG:").firstOrNull() != "no"

        findUsages(caretElement, options, highlightingMode, project, searchSuperDeclaration, testType)
    } finally {
        ExpressionsOfTypeProcessor.testLog = null
        if (logList.size > 0) {
            log = logList.sorted().joinToString("\n")
        }

        ExpressionsOfTypeProcessor.mode = ExpressionsOfTypeProcessor.Mode.ALWAYS_SMART
    }

    val filteringRules = AbstractFindUsagesTest.instantiateClasses<ImportFilteringRule>(mainFileText, "// FILTERING_RULES: ")
    val groupingRules = AbstractFindUsagesTest.instantiateClasses<UsageGroupingRule>(mainFileText, "// GROUPING_RULES: ")

    val filteredUsages = AbstractFindUsagesTest.getUsageAdapters(filteringRules, usageInfos)

    val usageFiles = filteredUsages.map { it.file.name }.distinct()
    val appendFileName = alwaysAppendFileName || usageFiles.size > 1

    val convertToString: (UsageInfo2UsageAdapter) -> String = { usageAdapter ->
        var groupAsString = groupingRules.joinToString(", ") { it.groupUsage(usageAdapter)?.presentableGroupText ?: "" }
        if (groupAsString.isNotEmpty()) {
            groupAsString = "($groupAsString) "
        }

        val usageType = executeOnPooledThreadInReadAction {
            AbstractFindUsagesTest.getUsageType(usageAdapter.element)
        }

        val usageTypeAsString = usageType?.toString() ?: "null"

        val usageChunks = ArrayList<TextChunk>()
        usageChunks.addAll(usageAdapter.presentation.text.asList())
        if (usageChunks.isNotEmpty()) {
            usageChunks[1] = TextChunk(usageChunks[1] .attributes, usageChunks[1].text.trimIndent())
        }
        usageChunks.add(1, TextChunk(TextAttributes(), " ")) // add space after line number

        buildString {
            if (appendFileName) {
                append("[").append(usageAdapter.file.name.let { javaNamesMap?.get(it) ?: it }).append("] ")
            }
            append(usageTypeAsString)
            append(" ")
            append(groupAsString)
            append(usageChunks.joinToString(""))
        }
    }

    val finalUsages = filteredUsages.map(convertToString).sorted()

    var results = File(rootPath, prefix + "results.txt")
    if (testType.isFir) {
        val firResults = File(rootPath, prefix + "fir.results.txt")
        if (firResults.exists()) {
            results = firResults
        }
    }

    KotlinTestUtils.assertEqualsToFile(testType.name, results, finalUsages.joinToString("\n"))

    if (log != null  && !ignoreLog) {
        KotlinTestUtils.assertEqualsToFile(testType.name, File(rootPath, prefix + "log"), log)

        // if log is empty then compare results with plain search
        try {
            ExpressionsOfTypeProcessor.mode = ExpressionsOfTypeProcessor.Mode.ALWAYS_PLAIN

            findUsagesAndCheckResults(
                mainFileText,
                prefix,
                rootPath,
                caretElement,
                options,
                project,
                alwaysAppendFileName = false,
                testType,
                javaNamesMap,
            )
        } finally {
            ExpressionsOfTypeProcessor.mode = ExpressionsOfTypeProcessor.Mode.ALWAYS_SMART
        }
    }
}

internal fun findUsages(
    targetElement: PsiElement,
    options: FindUsagesOptions?,
    highlightingMode: Boolean,
    project: Project,
    searchSuperDeclaration: Boolean = true,
    testType: FindUsageTestType = FindUsageTestType.DEFAULT,
): Collection<UsageInfo> {
    try {
        val handler: FindUsagesHandler = if (targetElement is PsiMember)
            JavaFindUsagesHandler(targetElement, JavaFindUsagesHandlerFactory(project))
        else {
            if (!searchSuperDeclaration) {
                setDialogsResult(CHECK_SUPER_METHODS_YES_NO_DIALOG, Messages.NO)
            }

            val findManagerImpl = FindManager.getInstance(project) as FindManagerImpl
            findManagerImpl.findUsagesManager.getFindUsagesHandler(targetElement, false)
                ?: error("Cannot find handler for: $targetElement")
        }

        @Suppress("NAME_SHADOWING")
        val options = options ?: handler.getFindUsagesOptions(null)

        handler.getFindUsagesOptions(null).let {
            if (it is KotlinFunctionFindUsagesOptions) it.isSearchForBaseMethod = searchSuperDeclaration
            else if (it is KotlinPropertyFindUsagesOptions) it.isSearchForBaseAccessors = searchSuperDeclaration
        }

        options.searchScope = GlobalSearchScope.allScope(project)

        val processor = CommonProcessors.CollectProcessor<UsageInfo>()
        for (psiElement in handler.primaryElements + handler.secondaryElements) {
            if (highlightingMode) {
                if (testType.isFir) {
                    ProgressManager.getInstance().run(
                        object : Task.Modal(project, "", false) {
                            override fun run(indicator: ProgressIndicator) {
                                project.runReadActionInSmartMode {
                                    for (reference in handler.findReferencesToHighlight(psiElement, options.searchScope)) {
                                        processor.process(UsageInfo(reference))
                                    }
                                }
                            }
                        },
                    )
                } else {
                    project.runReadActionInSmartMode {
                        for (reference in handler.findReferencesToHighlight(psiElement, options.searchScope)) {
                            processor.process(UsageInfo(reference))
                        }
                    }
                }
            } else {
                ProgressManager.getInstance().run(
                    object : Task.Modal(project, "", false) {
                        override fun run(indicator: ProgressIndicator) {
                            runReadAction {
                                handler.processElementUsages(psiElement, processor, options)
                            }
                        }
                    },
                )
            }
        }

        return processor.results
    } finally {
        clearDialogsResults()
    }
}