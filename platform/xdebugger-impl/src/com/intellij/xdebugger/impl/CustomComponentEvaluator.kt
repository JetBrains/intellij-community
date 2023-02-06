package com.intellij.xdebugger.impl

import com.intellij.xdebugger.frame.XFullValueEvaluator
import javax.swing.JComponent

abstract class CustomComponentEvaluator(name: String) : XFullValueEvaluator() {
  open fun createComponent(fullValue: String?) : JComponent? =  null;
}