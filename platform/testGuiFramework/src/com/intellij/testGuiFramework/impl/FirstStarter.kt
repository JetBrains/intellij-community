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
package com.intellij.testGuiFramework.impl

import com.intellij.openapi.util.ClassLoaderUtil
import com.intellij.util.lang.UrlClassLoader
import java.net.URL
import kotlin.concurrent.thread

class FirstStarter

fun main(args: Array<String>) {
  startRobotRoutine()
  startIdeMainRoutine(args)
}

private fun startIdeMainRoutine(args: Array<String>) {
  val defaultClassloader = FirstStarter::class.java.classLoader
  Thread.currentThread().contextClassLoader = defaultClassloader

  val ideMainClass = Class.forName("com.intellij.idea.Main", true, defaultClassloader)
  val mainMethod = ideMainClass.getMethod("main", Array<String>::class.java)
  mainMethod.isAccessible = true
  mainMethod.invoke(null, args)
}

private fun startRobotRoutine() {
  val robotClassLoader = createRobotClassLoader()

  fun awtIsNotStarted() = !(Thread.getAllStackTraces().keys.any { thread -> thread.name.toLowerCase().contains("awt-eventqueue") })

  thread(name = "GUI Test First Start: Wait AWT and Start", contextClassLoader = robotClassLoader) {
    while (awtIsNotStarted()) Thread.sleep(100)
    val firstStartClass = System.getProperty("idea.gui.test.first.start.class")
    Class.forName(firstStartClass).newInstance() as FirstStart
  }
}

fun createRobotClassLoader(): UrlClassLoader {
  val builder = UrlClassLoader.build()
    .urls(getUrlOfBaseClassLoader())
    .allowLock()
    .usePersistentClasspathIndexForLocalClassDirectories()
    .useCache()

  ClassLoaderUtil.addPlatformLoaderParentIfOnJdk9(builder)

  val newClassLoader = builder.get()
  return newClassLoader!!
}

fun getUrlOfBaseClassLoader(): List<URL> {
  val classLoader = Thread.currentThread().contextClassLoader
  val urlClassLoaderClass = classLoader.javaClass
  val getUrlsMethod = urlClassLoaderClass.methods.firstOrNull { it.name.toLowerCase() == "geturls" } ?: throw Exception(
    "Unable to get URLs for UrlClassLoader")
  @Suppress("UNCHECKED_CAST")
  val urlsListOrArray = getUrlsMethod.invoke(classLoader)
  return if (urlsListOrArray is Array<*>) urlsListOrArray.toList() as List<URL>
  else urlsListOrArray as List<URL>

}