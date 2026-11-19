// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.registerOrReplaceServiceInstance
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The extensions that apply to a project are the ones of the modules that [TechnologyFilter] accepts, see [TechnologyAwareExtensionPointName].
 */
@TestApplication
internal class TechnologyAwareExtensionPointNameTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val project: Project
    get() = projectFixture.get()

  private val rootArea: ExtensionsAreaImpl
    get() = ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl

  private val plainModule = DefaultPluginDescriptor("technology.plain")
  private val javaModule = DefaultPluginDescriptor("technology.java")

  private val technologyAware = TechnologyAwareExtensionPointName<Any>(POINT_NAME)
  private lateinit var point: ExtensionPointImpl<Any>

  @TestDisposable
  lateinit var testDisposable: Disposable

  @BeforeEach
  fun registerPoint() {
    rootArea.registerExtensionPoint(POINT_NAME, Any::class.java.name, ExtensionPoint.Kind.INTERFACE, true)
    point = rootArea.getExtensionPoint(POINT_NAME)
    point.registerExtensions(listOf(extension(Plain::class.java), extension(AlsoPlain::class.java)), plainModule, null)
    point.registerExtensions(listOf(extension(OfJava::class.java)), javaModule, null)
  }

  @AfterEach
  fun cleanUp() {
    rootArea.unregisterExtensionPoint(POINT_NAME)
  }

  @Test
  fun `every extension applies without a filter service`() {
    assertThat(TechnologyFilter.getInstance().isRestricted(project)).isFalse()
    assertThat(TechnologyFilter.getInstance().isApplicable(javaModule, project)).isTrue()

    assertThat(classesOf(technologyAware.extensionList(project))).containsExactly(Plain::class.java, AlsoPlain::class.java, OfJava::class.java)
    assertThat(technologyAware.extensionList(project)).isSameAs(point.extensionList)
  }

  @Test
  fun `a project without a restriction gets the cached list of the point`() {
    installFilter(FakeFilter(project, applicable = null))

    assertThat(technologyAware.extensionList(project)).isSameAs(point.extensionList)
  }

  @Test
  fun `a restricted project gets the extensions of the applicable modules only`() {
    val filter = installFilter(FakeFilter(project, applicable = setOf(plainModule.pluginId)))

    assertThat(classesOf(technologyAware.extensionList(project))).containsExactly(Plain::class.java, AlsoPlain::class.java)
    assertThat(classesOf(technologyAware.lazySequence(project).toList())).containsExactly(Plain::class.java, AlsoPlain::class.java)

    filter.applicable = emptySet()
    assertThat(technologyAware.extensionList(project)).isEmpty()

    filter.applicable = setOf(plainModule.pluginId, javaModule.pluginId)
    assertThat(classesOf(technologyAware.extensionList(project))).containsExactly(Plain::class.java, AlsoPlain::class.java, OfJava::class.java)
  }

  @Test
  fun `the safe helpers see only the applicable extensions`() {
    installFilter(FakeFilter(project, applicable = setOf(plainModule.pluginId)))

    val visited = ArrayList<Class<*>>()
    technologyAware.forEachExtensionSafe(project) { visited.add(it.javaClass) }
    assertThat(visited).containsExactly(Plain::class.java, AlsoPlain::class.java)

    assertThat(technologyAware.findFirstSafe(project) { it is AlsoPlain }).isInstanceOf(AlsoPlain::class.java)
    assertThat(technologyAware.findFirstSafe(project) { it is OfJava }).isNull()
    assertThat(technologyAware.computeSafeIfAny(project) { if (it is OfJava) "found" else null }).isNull()
    assertThat(technologyAware.computeSafeIfAny(project) { if (it is Plain) "found" else null }).isEqualTo("found")
  }

  @Test
  fun `a filter change and a plugin change notify the consumers of the point`() {
    val notified = ArrayList<String>()
    technologyAware.addChangeListener(testDisposable) { notified.add(it?.name ?: "all") }

    // a change of the filter gives the project of the change
    ApplicationManager.getApplication().messageBus.syncPublisher(TechnologyFilter.TOPIC).applicabilityChanged(project)
    assertThat(notified).containsExactly(project.name)

    // an extension that is registered or unregistered (a plugin load or unload) gives no project: every project is affected
    val extensionDisposable = Disposer.newDisposable()
    point.registerExtension(Plain(), plainModule, extensionDisposable)
    Disposer.dispose(extensionDisposable)
    assertThat(notified).containsExactly(project.name, "all", "all")
  }

  @Test
  fun `a project-level point is found in the area of the project`() {
    val projectArea = project.extensionArea as ExtensionsAreaImpl
    val projectPointName = "$POINT_NAME.project"
    projectArea.registerExtensionPoint(projectPointName, Any::class.java.name, ExtensionPoint.Kind.INTERFACE, true)
    try {
      projectArea.getExtensionPoint<Any>(projectPointName).registerExtensions(listOf(extension(Plain::class.java)), plainModule, null)
      projectArea.getExtensionPoint<Any>(projectPointName).registerExtensions(listOf(extension(OfJava::class.java)), javaModule, null)
      val projectTechnologyAware = TechnologyAwareProjectExtensionPointName<Any>(projectPointName)
      assertThat(classesOf(projectTechnologyAware.extensionList(project))).containsExactly(Plain::class.java, OfJava::class.java)

      val notified = ArrayList<Project?>()
      projectTechnologyAware.addChangeListener(project, testDisposable) { notified.add(it) }
      installFilter(FakeFilter(project, applicable = setOf(plainModule.pluginId)))
      assertThat(classesOf(projectTechnologyAware.extensionList(project))).containsExactly(Plain::class.java)

      ApplicationManager.getApplication().messageBus.syncPublisher(TechnologyFilter.TOPIC).applicabilityChanged(project)
      assertThat(notified).containsExactly(project)
    }
    finally {
      projectArea.unregisterExtensionPoint(projectPointName)
    }
  }

  private fun installFilter(filter: FakeFilter): FakeFilter {
    ApplicationManager.getApplication().registerOrReplaceServiceInstance(TechnologyFilter::class.java, filter, testDisposable)
    return filter
  }

  private fun extension(implementation: Class<*>): ExtensionDescriptor {
    return ExtensionDescriptor(implementation = implementation.name, os = null, orderId = null, order = LoadingOrder.ANY,
                               element = null, hasExtraAttributes = false)
  }

  private fun classesOf(extensions: List<Any>): List<Class<*>> = extensions.map { it.javaClass }
}

private const val POINT_NAME = "test.technologyAware.point"

/** Restricts [project] to the modules of [applicable]. A `null` value means no restriction. */
private class FakeFilter(private val project: Project, @Volatile var applicable: Set<PluginId>?) : TechnologyFilter {
  override fun isRestricted(project: Project): Boolean = project === this.project && applicable != null

  override fun isApplicable(module: PluginDescriptor, project: Project): Boolean {
    val applicable = applicable
    return project !== this.project || applicable == null || module.pluginId in applicable
  }
}

private class Plain

private class AlsoPlain

private class OfJava
