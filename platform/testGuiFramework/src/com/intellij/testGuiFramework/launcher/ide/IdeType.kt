package com.intellij.testGuiFramework.launcher.ide

import com.intellij.testGuiFramework.impl.FirstStart

/**
 * @author Sergey Karashevich
 */
abstract class IdeType(val platformPrefix: String, val ideJarName: String, val mainModule: String) {

  /**
   * returns an implementation of FirstStart class for a current IDE. It used to load this class by reflection on the first start in suite.
   * (@see GuiTestSuite)
   */
  abstract fun getFirstStartClass(): Class<out FirstStart>

}