/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.intellij.testGuiFramework.framework.GuiTestUtil.*
import org.fest.swing.edt.GuiActionRunner.execute
import org.fest.swing.edt.GuiTask
import org.junit.Assert.fail
import java.io.File

/**
 * @author Sergey Karashevich
 */
object IdeaGuiTestUtil{

  val LOG = Logger.getInstance("#com.intellij.testGuiFramework.idea.IdeaGuiTestUtil")

  fun setUpSdks() {

    var jdkHome: String? = getSystemPropertyOrEnvironmentVariable(JDK_HOME_FOR_TESTS)
    if (StringUtil.isEmpty(jdkHome) || !JdkUtil.checkForJdk(jdkHome!!)) {
      //than use bundled JDK
      jdkHome = getBundledJdLocation()
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

          val jdk_name = "JDK"
          val newJdk = javaSdk.createJdk(jdk_name, path.toString(), false)
          val foundJdk = ProjectJdkTable.getInstance().findJdk(newJdk.name, newJdk.sdkType.name)
          if (foundJdk == null) {
            ApplicationManager.getApplication().runWriteAction { ProjectJdkTable.getInstance().addJdk(newJdk) }
          }

          ApplicationManager.getApplication().runWriteAction {
            val modificator = newJdk.sdkModificator
            JavaSdkImpl.attachJdkAnnotations(modificator)
            modificator.commitChanges()
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
      jdkHome = getBundledJdLocation()
    }
    if (StringUtil.isEmpty(jdkHome) || !checkForJdk(jdkHome!!)) {
      fail("Please specify the path to a valid JDK using system property " + JDK_HOME_FOR_TESTS)
    }
    return jdkHome!!
  }

}