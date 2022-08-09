package org.jetbrains.completion.full.line.features

import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import org.jetbrains.completion.full.line.platform.FullLineLookupElement
import org.jetbrains.completion.full.line.platform.tests.FullLineCompletionTestCase
import org.jetbrains.completion.full.line.platform.tests.JavaProject
import org.jetbrains.completion.full.line.platform.tests.PythonProject
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance

class PythonAutoImportTest : AutoImportTest(), PythonProject {
    fun `test simple python`() {
        myFixture.enableInspections(PyUnresolvedReferencesInspection::class.java)
        doTest(
            "main.py",
            "custom_lib = MyCustomLib",
            "from pkg.lib import MyCustomLib"
        )
    }
}

class JavaAutoImportTest : AutoImportTest(), JavaProject {
    fun `test simple java`() = doTest(
        "Main.java",
        "CustomLib lib = new CustomLib()",
        "import pkg.CustomLib;"
    )
}

//TODO: uncomment test when auto-import will be enabled in kotlin
//class KotlinAutoImportTest : AutoImportTest(), KotlinProject {
//    fun `test simple kotlin`() {
//        doTest(
//            "Main.kt",
//            "val lib = CustomLib()",
//            "import pkg.CustomLib;"
//        )
//    }
//}

abstract class AutoImportTest : FullLineCompletionTestCase() {
    override fun getBasePath() = "testData/completion/features/auto-import"

    /**
     * Testing auto-import feature
     * @param filename file name, to be evaluated
     * @param variant will be shown in popup as full line suggestion
     * @param expectedLine expected import line, which must be added only after auto-import
     */
    protected fun doTest(filename: String, variant: String, expectedLine: String) {
        myFixture.copyDirectoryToProject(getTestName(false), "")
        myFixture.configureByFile(filename)

        // Check that import is missing
        assertFalse("Import is already in file", myFixture.file.text.contains(expectedLine))

        myFixture.completeFullLine(variant)
        myFixture.lookup.currentItem = myFixture.lookupElements?.firstIsInstance<FullLineLookupElement>()
        myFixture.finishLookup('\n')

        // Check that import was added
        assertTrue("Import was not added", myFixture.file.text.contains(expectedLine))
    }
}
