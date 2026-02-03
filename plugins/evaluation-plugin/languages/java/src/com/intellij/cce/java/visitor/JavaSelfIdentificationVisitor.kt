package com.intellij.cce.java.visitor

import com.intellij.cce.core.Language
import com.intellij.cce.visitor.SelfIdentificationVisitor

class JavaSelfIdentificationVisitor : SelfIdentificationVisitor() {
  override val language: Language = Language.JAVA
}