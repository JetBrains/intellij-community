package org.jetbrains.completion.full.line.java.formatters

import com.intellij.ide.highlighter.JavaFileType
import org.jetbrains.completion.full.line.language.formatters.PsiCodeFormatterTest

abstract class JavaPsiCodeFormatterTest : PsiCodeFormatterTest(JavaPsiCodeFormatter(), JavaFileType.INSTANCE)
