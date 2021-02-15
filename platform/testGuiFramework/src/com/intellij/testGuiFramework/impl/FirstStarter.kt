// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.impl

import com.intellij.util.lang.UrlClassLoader
import java.net.URL
import java.nio.file.Paths
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

private fun createRobotClassLoader(): UrlClassLoader {
  return UrlClassLoader.build()
    .files(getUrlOfBaseClassLoader().map { Paths.get(it.path) })
    .usePersistentClasspathIndexForLocalClassDirectories()
    .useCache()
    .parent(ClassLoader.getPlatformClassLoader()).get()
}

private fun getUrlOfBaseClassLoader(): List<URL> {
  val classLoader = Thread.currentThread().contextClassLoader
  val urlClassLoaderClass = classLoader.javaClass
  val getUrlsMethod = urlClassLoaderClass.methods.firstOrNull { it.name.toLowerCase() == "geturls" } ?: throw Exception(
    "Unable to get URLs for UrlClassLoader")
  @Suppress("UNCHECKED_CAST")
  val urlsListOrArray = getUrlsMethod.invoke(classLoader)
  if (urlsListOrArray is Array<*>) {
    return urlsListOrArray.toList() as List<URL>
  }
  else {
    return urlsListOrArray as List<URL>
  }
}
