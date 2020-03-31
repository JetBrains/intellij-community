package org.jetbrains.idea.devkit.kotlin.inspections.internal

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.idea.devkit.inspections.PluginModuleTestCase
import org.jetbrains.idea.devkit.inspections.internal.UndesirableClassUsageInspection

class KtUndesirableClassUsageInspectionTest : PluginModuleTestCase() {

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    myFixture.enableInspections(UndesirableClassUsageInspection())
  }

  fun testUsages() {
    doTest("javax.swing.JList", "com.intellij.ui.components.JBList")
    doTest("javax.swing.JTable", "com.intellij.ui.table.JBTable")
    doTest("javax.swing.JTree", "com.intellij.ui.treeStructure.Tree")
    doTest("javax.swing.JScrollPane", "com.intellij.ui.components.JBScrollPane")
    doTest("javax.swing.JTabbedPane", "com.intellij.ui.components.JBTabbedPane")
    doTest("javax.swing.JComboBox", "com.intellij.openapi.ui.ComboBox")
    doTest("com.intellij.util.QueryExecutor", "com.intellij.openapi.application.QueryExecutorBase")
    doTest("java.awt.image.BufferedImage", "UIUtil.createImage()")
  }

  private fun doTest(classFqn: String, replacementText: String) {
    val shortName = StringUtil.getShortName(classFqn)
    myFixture.addClass("package " + StringUtil.getPackageName(classFqn) + ";" +
                       "public class " + shortName + " {}")

    myFixture.configureByText("Testing.kt", """

      import $classFqn

      class Testing {
         val name = <warning descr="Please use '$replacementText' instead">$shortName()</warning>;
         fun method() {
         <warning descr="Please use '$replacementText' instead">$shortName()</warning>;
         }
         fun methodParam(p: $shortName = <warning descr="Please use '$replacementText' instead">$shortName()</warning>):String {
           return "${"\$p"}"
         }
       }
       """.trimMargin())
    myFixture.testHighlighting()
  }


}