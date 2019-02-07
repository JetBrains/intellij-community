// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.devkit.build

import com.intellij.util.PathUtil
import org.jetbrains.jps.builders.JpsBuildTestCase
import org.jetbrains.jps.devkit.model.JpsIdeaSdkProperties
import org.jetbrains.jps.devkit.model.JpsIdeaSdkType
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.model.JpsElementFactory

class JpsPluginBuildTest : JpsBuildTestCase() {
  fun `test show proper error message if jdk type is invalid`() {
    addModule("m", PathUtil.getParentPath(createFile("src/A.java", "class A{}")))
    val jdkName = jdk.parent.name
    val sdk = myModel.global.libraryCollection.findLibrary(jdkName)!!
    myModel.global.libraryCollection.removeLibrary(sdk)
    val sdkProperties = JpsElementFactory.getInstance().createSimpleElement(JpsIdeaSdkProperties(null, ""))
    myModel.global.addSdk(jdkName, null, null, JpsIdeaSdkType.INSTANCE, sdkProperties)
    val result = buildAllModules()
    result.assertFailed()
    val errorMessage = assertOneElement(result.getMessages(BuildMessage.Kind.ERROR))
    assertEquals("Cannot find JDK for module 'm': '${jdkName}' points to IntelliJ Platform Plugin SDK", errorMessage.messageText)
  }
}