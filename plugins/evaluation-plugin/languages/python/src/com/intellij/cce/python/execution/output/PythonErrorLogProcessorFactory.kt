package com.intellij.cce.python.execution.output

class PythonErrorLogProcessorFactory {

  // Creates a PythonErrorLogProcessor according to the testing framework. The default is UnittestPythonErrorLogProcessor.
  fun createProcessor(testingFramework: String?): PythonErrorLogProcessor {
    if (testingFramework == "pytest") return PytestPythonErrorLogProcessor()
    return UnittestPythonErrorLogProcessor()
  }
}
