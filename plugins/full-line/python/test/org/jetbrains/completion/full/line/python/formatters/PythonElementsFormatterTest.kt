package org.jetbrains.completion.full.line.python.formatters

class PythonElementsFormatterTest : PythonCodeFormatterTest() {
  fun `test numerical`() {
    testFile("test-elements/numerical.py")
  }

  fun `test parameter list without closed bracket`() {
    testCodeFragment("def nginx(_config ", "def nginx(_config ", 18)
  }

  fun `test function params without type`() {
    testCodeFragment("def checkout_branch(branch):\n    return None", "def checkout_branch(branch):\n\treturn None")
  }

  fun `test function params with type`() {
    testCodeFragment("def checkout_branch(branch: str):\n    return None", "def checkout_branch(branch: str):\n\treturn None")
  }

  fun `test function params with type and default value`() {
    testCodeFragment("def checkout_branch(branch: str=\"master\"):\n    return None",
                     "def checkout_branch(branch: str=\"master\"):\n\treturn None")
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

  fun `test strings`() {
    testFile("test-elements/strings.py")
  }

  fun `test imports`() {
    testFile("test-elements/imports.py")
  }
}
