// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.sanity

import com.intellij.idea.IJIgnore
import com.intellij.psi.search.GlobalSearchScopes
import com.intellij.structuralsearch.Matcher
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration
import com.intellij.structuralsearch.plugin.util.CollectingMatchResultSink
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralSearchProfile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.junit.Ignore
import java.io.File
import kotlin.random.Random

/**
 * Test class for spotting Kotlin constructs that are not matched by structural search.
 */
@IJIgnore(issue = "KTIJ-291") @Ignore("Useful for local testing")
class KotlinSSSanityTest : BasePlatformTestCase() {
    private val myConfiguration = SearchConfiguration().apply {
        name = "SSR"
        matchOptions.setFileType(KotlinFileType.INSTANCE)
    }
    private var random = Random(System.currentTimeMillis())

    override fun setUp() {
        super.setUp()
        random = Random(project.hashCode())
    }

    override fun getProjectDescriptor(): KotlinLightSanityProjectDescriptor = KotlinLightSanityProjectDescriptor()

    private fun doTest(source: String): Boolean {
        myFixture.configureByText(KotlinFileType.INSTANCE, source)

        val tree = KtPsiFactory(project).createFile(source).children
        val subtree = SanityTestElementPicker.pickFrom(tree)
        if (subtree == null) {
            println("No element picked.")
            return true
        }
        if (KotlinStructuralSearchProfile.TYPED_VAR_PREFIX in subtree.text) {
            println("The search pattern contains the typed var prefix. Aborting.")
            return true
        }

        println()
        println("Search pattern [${subtree::class.toString().split('.').last()}]:")
        println()
        println(subtree.text.lines().first())
        if (subtree.text.lines().size > 1) println(subtree.text.lines().drop(1).joinToString("\n").trimIndent())
        println()

        val matchOptions = myConfiguration.matchOptions.apply {
            fillSearchCriteria(subtree.text)
            setFileType(KotlinFileType.INSTANCE)
            scope = GlobalSearchScopes.openFilesScope(project)
        }
        val matcher = Matcher(project, matchOptions)
        val sink = CollectingMatchResultSink()
        matcher.findMatches(sink)

        return sink.matches.isNotEmpty()
    }

    /** Picks a random .kt file from this project and returns its content and PSI tree. */
    private fun localKotlinSource(): List<File> {
        val kotlinFiles = File("../../../../../../community/platform/").walk().toList().filter { it.extension == "kt" && "Predefined" !in it.name }
        assert(kotlinFiles.any()) { "No .kt source found." }
        return kotlinFiles
    }

    fun testLocalSSS() {
        val sources = localKotlinSource()
        assertContainsElements("Couldn't find Kotlin source code", sources)

        repeat(1) {
            val source = sources.random()
            println("- ${source.absolutePath}")
            assert(doTest(source.readText())) { "No match found." }
            println("Matched\n")
        }
    }

}