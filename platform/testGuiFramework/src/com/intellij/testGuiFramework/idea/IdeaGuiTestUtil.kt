// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.idea

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JdkUtil
import com.intellij.openapi.projectRoots.JdkUtil.checkForJdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.util.io.FileUtil.pathsEqual
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.framework.GuiTestUtil.JDK_HOME_FOR_TESTS
import com.intellij.testGuiFramework.framework.GuiTestUtil.getSystemPropertyOrEnvironmentVariable
import org.fest.swing.edt.GuiActionRunner.execute
import org.fest.swing.edt.GuiTask
import org.junit.Assert.fail
import java.io.File

/**
 * @author Sergey Karashevich
 */
object IdeaGuiTestUtil{

  val LOG: Logger = Logger.getInstance("#com.intellij.testGuiFramework.idea.IdeaGuiTestUtil")

  fun setUpSdks() {

    var jdkHome: String? = getSystemPropertyOrEnvironmentVariable(JDK_HOME_FOR_TESTS)
    if (StringUtil.isEmpty(jdkHome) || !JdkUtil.checkForJdk(jdkHome!!)) {
      //than use bundled JDK
      jdkHome = GuiTestUtil.bundledJdkLocation
    }
    val jdkPath = File(jdkHome)

    execute(object : GuiTask() {
      @Throws(Throwable::class)
      override fun executeInEDT() {
        ApplicationManager.getApplication().runWriteAction {
          LOG.info(String.format("Setting JDK: '%1\$s'", jdkPath.path))
          setJdkPath(jdkPath)
        }
      }
    })
  }

  fun setJdkPath(path: File) {
    if (JdkUtil.checkForJdk(path)) {
      ApplicationManager.getApplication().assertWriteAccessAllowed()
      var chosenJdk: Sdk? = null

      for (jdk in ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance())) {
        if (pathsEqual(jdk.homePath, path.path)) {
          chosenJdk = jdk
          break
        }
      }

      if (chosenJdk == null) {
        if (path.isDirectory) {

          val javaSdk = JavaSdk.getInstance() ?: return

          //in case of running different from IntelliJ or Android Studio IDE (PyCharm for example)

          val newJdk = javaSdk.createJdk("JDK", path.toString(), false)
          val foundJdk = ProjectJdkTable.getInstance().findJdk(newJdk.name, newJdk.sdkType.name)
          if (foundJdk == null) {
            ApplicationManager.getApplication().runWriteAction { ProjectJdkTable.getInstance().addJdk(newJdk) }
          }
        }
        else {
          throw IllegalStateException("The resolved path '" + path.path + "' was not found")
        }
      }
    }
  }

  fun getSystemJdk(): String {
    var jdkHome: String? = getSystemPropertyOrEnvironmentVariable(JDK_HOME_FOR_TESTS)
    if (StringUtil.isEmpty(jdkHome) || !checkForJdk(jdkHome!!)) {
      //than use bundled JDK
      jdkHome = GuiTestUtil.bundledJdkLocation
    }
    if (StringUtil.isEmpty(jdkHome) || !checkForJdk(jdkHome)) {
      fail("Please specify the path to a valid JDK using system property " + JDK_HOME_FOR_TESTS)
    }
    return jdkHome
  }

}