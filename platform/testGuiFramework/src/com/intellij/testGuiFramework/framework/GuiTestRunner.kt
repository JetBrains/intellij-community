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
package com.intellij.testGuiFramework.framework

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import org.fest.swing.image.ScreenshotTaker
import org.junit.After
import org.junit.Before
import org.junit.internal.runners.model.ReflectiveCallable
import org.junit.internal.runners.statements.Fail
import org.junit.internal.runners.statements.RunAfters
import org.junit.internal.runners.statements.RunBefores
import org.junit.runner.notification.RunNotifier
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.InitializationError
import org.junit.runners.model.Statement
import org.junit.runners.model.TestClass

import java.awt.*
import java.io.File
import java.lang.management.GarbageCollectorMXBean
import java.lang.management.ManagementFactory
import java.lang.management.MemoryMXBean
import java.lang.reflect.Method
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Collections
import java.util.Date

import org.junit.Assert.assertNotNull

class GuiTestRunner @Throws(InitializationError::class)
constructor(testClass: Class<*>) : BlockJUnit4ClassRunner(testClass) {

  private var myTestClass: TestClass? = null

  private val myGarbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans()
  private val myMemoryMXBean = ManagementFactory.getMemoryMXBean()

  override fun runChild(method: FrameworkMethod, notifier: RunNotifier) {
    if (GuiTestUtil.doesIdeHaveFatalErrors()) {
      notifier.fireTestIgnored(describeChild(method))
      LOG.error(String.format("Skipping test '%1\$s': a fatal error has occurred in the IDE", method.name))
      notifier.pleaseStop()
    }
    else {
      super.runChild(method, notifier)
    }
  }



  @Throws(Exception::class)
  private fun loadClassesWithNewPluginClassLoader() {

    IdeTestApplication.getInstance() //ensure that IDEA has been initialized.
    val testParentPluginAnnotation = testClass.getAnnotation(ParentPlugin::class.java)
    assertNotNull(testParentPluginAnnotation)

    val dependentPluginId: String = testParentPluginAnnotation.pluginId
    val classLoader = Thread.currentThread().contextClassLoader
    val classPath = testClass.javaClass.canonicalName.replace(".", "/") + ".class"
    val resource = classLoader.getResource(classPath)
    assertNotNull(resource)
    val pathToTestClass = resource!!.path
    val containingFolderPath = pathToTestClass.substring(0, pathToTestClass.length - classPath.length)

    val urlToTestClass = File(containingFolderPath).toURI().toURL()

    val parentPluginDescriptor = PluginManager.getPlugin(PluginId.getId(dependentPluginId))
    assertNotNull(parentPluginDescriptor)
    val parentPluginClassLoader = parentPluginDescriptor!!.pluginClassLoader
    val classLoaders = arrayOf(parentPluginClassLoader)

    val testPluginId = dependentPluginId + ".guitest"
    val testPluginClassLoader = PluginClassLoader(listOf(urlToTestClass), classLoaders, PluginId.getId(testPluginId), null, null)

    Thread.currentThread().contextClassLoader = testPluginClassLoader

    val testClass = testClass.javaClass
    myTestClass = TestClass(testPluginClassLoader.loadClass(testClass.name))
  }

  companion object {

    private val LOG = Logger.getInstance(GuiTestRunner::class.java)
  }


}
