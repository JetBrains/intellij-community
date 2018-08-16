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
package com.intellij.testGuiFramework.recorder.compile

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.testGuiFramework.recorder.actions.PerformScriptAction
import java.io.File
import java.net.URL
import java.nio.file.Paths
import java.util.jar.JarFile

object KotlinCompileUtil {

  private val localCompiler: LocalCompiler by lazy { LocalCompiler() }

  fun compileAndRun(codeString: String) {
    localCompiler.compileAndRunOnPooledThread(ScriptWrapper.wrapScript(codeString),
                                              getAllUrls().map { Paths.get(it.toURI()).toFile().path })
  }

  private fun getAllUrls(): List<URL> {
    if (ServiceManager::class.java.classLoader.javaClass.name.contains("Launcher\$AppClassLoader")) {
      //lets substitute jars with a common lib dir to avoid Windows long path error
      val urls = ServiceManager::class.java.classLoader.forcedUrls()
      val libUrl = urls.first { url ->
        (url.file.endsWith("idea.jar") && File(url.path).parentFile.name == "lib")
      }.getParentURL()
      urls.filter { url -> !url.file.startsWith(libUrl.file) }.plus(libUrl).toSet()
      if (!ApplicationManager.getApplication().isUnitTestMode)
        urls.plus(ServiceManager::class.java.classLoader.forcedBaseUrls())
      return urls.toList()
    }

    val set = mutableSetOf<URL>()
    set.addAll(ServiceManager::class.java.classLoader.forcedUrls())
    set.addAll(PerformScriptAction::class.java.classLoader.forcedUrls())
    set.addAll(PerformScriptAction::class.java.classLoader.forcedBaseUrls())
    if (!ApplicationManager.getApplication().isUnitTestMode)
      set.addAll(ServiceManager::class.java.classLoader.forcedBaseUrls())
    expandClasspathInJar(set)
    return set.toList()
  }

  private fun expandClasspathInJar(setOfUrls: MutableSet<URL>) {
    val classpathUrl = setOfUrls.firstOrNull { Regex("classpath\\d*.jar").containsMatchIn(it.path) || it.path.endsWith("pathing.jar") }
    if (classpathUrl != null) {
      val classpathFile = Paths.get(classpathUrl.toURI()).toFile()
      if (!classpathFile.exists()) return
      val classpathLine = JarFile(classpathFile).manifest.mainAttributes.getValue("Class-Path")
      val classpathList = classpathLine.split(" ").filter { it.startsWith("file") }.map { URL(it) }
      setOfUrls.addAll(classpathList)
      setOfUrls.remove(classpathUrl)
    }
  }

  private fun URL.getParentURL() = File(this.file).parentFile.toURI().toURL()!!

  private fun ClassLoader.forcedUrls(): List<URL> {
    var methodName = "getUrls"
    val methodAlternativeName = "getURLs"

    if (this.javaClass.methods.any { mtd -> mtd.name == methodAlternativeName }) methodName = methodAlternativeName
    val method = this.javaClass.getMethod(methodName)
    method.isAccessible
    val methodResult = method.invoke(this)
    val myList: List<*> = (methodResult as? Array<*>)?.asList() ?: methodResult as List<*>
    return myList.filterIsInstance(URL::class.java)
  }

  private fun ClassLoader.forcedBaseUrls(): List<URL> {
    try {
      return ((this.javaClass.getMethod("getBaseUrls").invoke(this) as? List<*>)!!
        .filterIsInstance(URL::class.java)
        .map { if (it.protocol == "jar") URL(it.toString().removeSurrounding("jar:", "!/")) else it })

    }
    catch (e: NoSuchMethodException) {
      return emptyList()
    }
  }

}