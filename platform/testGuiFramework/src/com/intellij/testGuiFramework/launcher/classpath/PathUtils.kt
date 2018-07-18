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
package com.intellij.testGuiFramework.launcher.classpath

import com.intellij.testGuiFramework.launcher.system.SystemInfo
import java.io.File

class PathUtils(idePath: String) {

  val myIdeDir: File = File(idePath)

  companion object {
    fun getJreBinPath(): String {
      val homePath = System.getProperty("java.home")
      val jreDir = File(homePath)
      val homeDir = File(jreDir.parent)
      val binDir = File(homeDir, "bin")
      val javaName: String = if (System.getProperty("os.name").toLowerCase().contains("win")) "java.exe" else "java"
      return File(binDir, javaName).path
    }
  }

  fun getIdeRootContentDir(): File {
    when (SystemInfo.getSystemType()) {
      SystemInfo.SystemType.WINDOWS -> return myIdeDir
      SystemInfo.SystemType.MAC -> return File(myIdeDir, "Contents")
      SystemInfo.SystemType.UNIX -> return myIdeDir
    }
  }

  fun getJdkDir(): File = File(getJreBinPath())
    .parentFile //bin dir
    .parentFile //home dir

  fun getIdeLibDir(): File = File(getIdeRootContentDir(), "lib")

  fun getIdePluginsDir(): File = File(getIdeRootContentDir(), "plugins")

  fun getJUnitJarFile(): File = getIdeLibDir().listFiles().find { it.name.contains("junit-4.12.jar") }!!

  fun getTestGuiFrameworkDir(): File {
    val pluginDir = getIdePluginsDir().listFiles().find { it.name.toLowerCase().contains("testguiframework") }!!
    return File(pluginDir, "lib")
  }

  fun getTestGuiFrameworkJar(): File {
    return getTestGuiFrameworkDir().listFiles().find { it.name.toLowerCase().contains("testguiframework") && it.name.endsWith(".jar") }!!
  }

  fun makeClassPathBuilder(): ClassPathBuilder {
    return ClassPathBuilder(ideaLibPath = getIdeLibDir().path,
                            jdkPath = getJdkDir().path,
                            jUnitPath = getJUnitJarFile().path,
                            festLibsPath = getTestGuiFrameworkDir().path,
                            testGuiFrameworkPath = getTestGuiFrameworkJar().path)
  }

}

