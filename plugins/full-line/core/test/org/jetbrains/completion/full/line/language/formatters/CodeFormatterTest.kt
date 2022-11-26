package org.jetbrains.completion.full.line.language.formatters

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.completion.full.line.language.CodeFormatter

abstract class CodeFormatterTest(val formatter: CodeFormatter) : BasePlatformTestCase()
