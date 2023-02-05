package com.intellij.xdebugger.frame

import javax.swing.JComponent

abstract class CustomComponentEvaluator(name: String) : XFullValueEvaluator() {
  open fun createComponent(fullValue: String?) : JComponent? =  null;
}