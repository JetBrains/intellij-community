package org.jetbrains.completion.full.line.python.formatters

class PythonElementsFormatterTest : PythonCodeFormatterTest() {
  override val formatter = PythonCodeFormatter()

  fun `test numerical`() {
    testFile("test-elements/v2/numerical.py")
  }

  fun `test strings`() {
    testFile("test-elements/v2/strings.py")
  }

  fun `test imports`() {
    testFile("test-elements/v2/imports.py")
  }

  fun `test parameter list without closed bracket`() {
    testCodeFragment("def nginx(_config ", "def nginx(_config ", 18)
  }

  fun `test function params without type`() {
    testCodeFragment("def checkout_branch(branch):\n    return None", "def checkout_branch(branch):⇥return None")
  }

  fun `test function params with type`() {
    testCodeFragment("def checkout_branch(branch: str):\n    return None", "def checkout_branch(branch: str):⇥return None")
  }

  fun `test function params with type and default value`() {
    testCodeFragment("def checkout_branch(branch: str=\"master\"):\n    return None",
                     "def checkout_branch(branch: str = \"master\"):⇥return None")
  }

  fun `test not fully filled import`() {
    testCodeFragment("import ", "import ")
  }

  fun `test not fully filled from import`() {
    testCodeFragment("from tqdm import ", "from tqdm import ")
  }

  fun `test inside not fully filled from import`() {
    testCodeFragment("from  import tqdm", "from ", 5)
  }

  fun `test incomplete string`() {
    testCodeFragment("a = \"simple ", "a = \"simple ")
    testCodeFragment("a = \"simple str", "a = \"simple str")
    testCodeFragment("a = \"simple string\"", "a = \"simple string\"")

    testCodeFragment("a = 'simple ", "a = \"simple ")
    testCodeFragment("a = 'simple str", "a = \"simple str")
    testCodeFragment("a = 'simple string'", "a = \"simple string\"")

    testCodeFragment("a = \"<caret>simple string\"", "a = \"")
    testCodeFragment("a = \"simp<caret>le string\"", "a = \"simp")
    testCodeFragment("a = \"simple str<caret>ing\"", "a = \"simple str")
    testCodeFragment("a = \"simple string<caret>\"", "a = \"simple string")

    testCodeFragment("a = '<caret>simple string'", "a = \"")
    testCodeFragment("a = 'simp<caret>le string'", "a = \"simp")
    testCodeFragment("a = 'simple str<caret>ing'", "a = \"simple str")
    testCodeFragment("a = 'simple string<caret>'", "a = \"simple string")
  }
}
