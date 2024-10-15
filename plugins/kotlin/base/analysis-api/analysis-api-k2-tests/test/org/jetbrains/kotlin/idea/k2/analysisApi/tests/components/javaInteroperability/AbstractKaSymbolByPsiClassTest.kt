package org.jetbrains.kotlin.idea.k2.analysisApi.tests.components.javaInteroperability

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModuleProvider
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.KaClassifierBodyRenderer
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import java.io.File

abstract class AbstractKaSymbolByPsiClassTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun runInDispatchThread(): Boolean = false

    @OptIn(KaExperimentalApi::class)
    fun doTest(testPath: String) {
        val file = File(testPath)
        val fileText = FileUtil.loadFile(file)
        val classFqName = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// CLASS_FQ_NAME: ")
            ?: error("CLASS_FQ_NAME directive not found")
        val rendered = runReadAction {
            val psiClass =
                JavaFullClassNameIndex.getInstance().getClasses(classFqName, project, GlobalSearchScope.allScope(project)).single()

            analyze(psiClass.getKaModule(project, useSiteModule = null)) {
                val kaClassSymbol = psiClass.namedClassSymbol ?: return@analyze "<KaNamedClassSymbol cannot be created>"
                // render should always trigger analysis and thus FIR symbol creation
                kaClassSymbol.render(KaDeclarationRendererForSource.WITH_QUALIFIED_NAMES.with {
                    classifierBodyRenderer = KaClassifierBodyRenderer.BODY_WITH_MEMBERS
                })
            }
        }

        KotlinTestUtils.assertEqualsToFile(file.parentFile.resolve(file.nameWithoutExtension + ".txt"), rendered)

    }

    override fun getProjectDescriptor(): LightProjectDescriptor {
        // tested against JDK classes
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()
    }

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }
}