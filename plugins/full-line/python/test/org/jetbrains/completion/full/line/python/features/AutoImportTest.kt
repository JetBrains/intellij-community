package org.jetbrains.completion.full.line.python.features

import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import org.jetbrains.completion.full.line.features.AutoImportTest
import org.jetbrains.completion.full.line.python.tests.pyProjectDescriptor

class PythonAutoImportTest : AutoImportTest() {
  override fun getProjectDescriptor() = pyProjectDescriptor(basePath)

  fun `test simple python`() {
    myFixture.enableInspections(PyUnresolvedReferencesInspection::class.java)
    doTest(
      "main.py",
      "custom_lib = MyCustomLib",
      "from pkg.lib import MyCustomLib"
    )
  }
}
