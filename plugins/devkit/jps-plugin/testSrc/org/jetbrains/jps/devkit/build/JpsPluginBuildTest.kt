// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.devkit.build

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.util.PathUtil
import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContent
import org.jetbrains.jps.builders.CompileScopeTestBuilder
import org.jetbrains.jps.builders.JpsBuildTestCase
import org.jetbrains.jps.devkit.model.JpsIdeaSdkProperties
import org.jetbrains.jps.devkit.model.JpsIdeaSdkType
import org.jetbrains.jps.devkit.model.JpsPluginModuleProperties
import org.jetbrains.jps.devkit.model.JpsPluginModuleType
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.java.JpsJavaLibraryType
import org.jetbrains.jps.model.java.JpsNativeLibraryRootType
import org.jetbrains.jps.model.library.JpsOrderRootType
import java.io.File
import kotlin.io.path.Path

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

  fun `test build native libraries`() {
    val pluginModuleProperties = JpsElementFactory.getInstance().createSimpleElement(JpsPluginModuleProperties(null, null))
    val m = JpsElementFactory.getInstance().createModule("m", JpsPluginModuleType.INSTANCE, pluginModuleProperties)
    myProject.addModule(m)
    val sandboxDir = FileUtil.createTempDirectory("sandbox", null)
    val sdkProperties = JpsElementFactory.getInstance().createSimpleElement(JpsIdeaSdkProperties(sandboxDir.systemIndependentPath, jdk.parent.name))
    val pluginSdk = myModel.global.addSdk("plugin sdk", null, null, JpsIdeaSdkType.INSTANCE, sdkProperties)
    setupModuleSdk(m, pluginSdk.properties)

    val library = m.libraryCollection.addLibrary("l", JpsJavaLibraryType.INSTANCE)
    m.dependenciesList.addLibraryDependency(library)
    val libDir = Path(createDir("lib"))
    directoryContent {
      zip("a.jar") { file("a.txt") }
    }.generate(libDir)

    library.addRoot(libDir.resolve( "a.jar"), JpsOrderRootType.COMPILED)
    library.addRoot(Path(createFile("lib/a.so")), JpsNativeLibraryRootType.INSTANCE)

    doBuild(CompileScopeTestBuilder.rebuild().allModules().allArtifacts()).assertSuccessful()

    File(sandboxDir, "plugins/${m.name}").assertMatches(directoryContent {
      dir("lib") {
        zip("a.jar") { file("a.txt") }
        file("a.so")
      }
    })
  }
}