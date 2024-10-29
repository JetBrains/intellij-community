package com.intellij.cce.visitor

import com.intellij.cce.core.Language

class JavaSelfIdentificationVisitor : SelfIdentificationVisitor() {
  override val language: Language = Language.JAVA
}