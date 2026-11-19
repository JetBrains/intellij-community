// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.TechnologyFilter
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.registerOrReplaceServiceInstance
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * A language extension gives the extensions that apply to a project, see [LanguageExtension.allForLanguage] with a project
 * and [TechnologyFilter].
 */
@TestApplication
internal class LanguageExtensionTechnologiesTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val project: Project
    get() = projectFixture.get()

  private val rootArea: ExtensionsAreaImpl
    get() = ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl

  private val plainModule = DefaultPluginDescriptor(PluginId.getId("technology.language.plain"), javaClass.classLoader)
  private val javaModule = DefaultPluginDescriptor(PluginId.getId("technology.language.java"), javaClass.classLoader)
  private val kotlinModule = DefaultPluginDescriptor(PluginId.getId("technology.language.kotlin"), javaClass.classLoader)
  private val language: Language = PlainTextLanguage.INSTANCE
  private lateinit var point: ExtensionPointImpl<LanguageExtensionPoint<Any>>
  private lateinit var extension: LanguageExtension<Any>

  private val disposable: Disposable = Disposer.newDisposable("LanguageExtensionTechnologiesTest")

  @BeforeEach
  fun registerPoint() {
    rootArea.registerExtensionPoint(POINT_NAME, LanguageExtensionPoint::class.java.name, ExtensionPoint.Kind.BEAN_CLASS, true)
    point = rootArea.getExtensionPoint(POINT_NAME)
    point.registerExtension(bean(language, Plain(), plainModule), plainModule, disposable)
    point.registerExtension(bean(language, OfJava(), javaModule), javaModule, disposable)
    point.registerExtension(bean(Language.ANY, OfKotlinForAnyLanguage(), kotlinModule), kotlinModule, disposable)
    extension = LanguageExtension(POINT_NAME)
  }

  @AfterEach
  fun cleanUp() {
    Disposer.dispose(disposable)
    extension.clearCache(language)
    extension.clearCache(Language.ANY)
    rootArea.unregisterExtensionPoint(POINT_NAME)
  }

  @Test
  fun `every extension applies to a project without a restriction`() {
    assertThat(classesOf(extension.allForLanguage(language, project))).containsExactly(Plain::class.java, OfJava::class.java)
    assertThat(extension.allForLanguage(language, project)).isSameAs(extension.allForLanguage(language))
    assertThat(classesOf(extension.allForLanguageOrAny(language, project)))
      .containsExactly(Plain::class.java, OfJava::class.java, OfKotlinForAnyLanguage::class.java)
  }

  @Test
  fun `an extension of a module that does not apply is skipped for the project only`() {
    val filter = installFilter(setOf(plainModule, kotlinModule))
    assertThat(classesOf(extension.allForLanguage(language, project))).containsExactly(Plain::class.java)
    assertThat(classesOf(extension.allForLanguageOrAny(language, project))).containsExactly(Plain::class.java, OfKotlinForAnyLanguage::class.java)
    // The callers without a project see every extension: the extensions are the same for every project.
    assertThat(classesOf(extension.allForLanguage(language))).containsExactly(Plain::class.java, OfJava::class.java)

    filter.applicable = setOf(plainModule)
    assertThat(classesOf(extension.allForLanguageOrAny(language, project))).containsExactly(Plain::class.java)
  }

  @Test
  fun `the extensions of a key follow the filter too`() {
    assertThat(classesOf(extension.forKey(language, project))).containsExactly(Plain::class.java, OfJava::class.java)
    assertThat(extension.forKey(language, project)).isSameAs(extension.forKey(language))

    installFilter(setOf(plainModule, kotlinModule))
    assertThat(classesOf(extension.forKey(language, project))).containsExactly(Plain::class.java)
    assertThat(classesOf(extension.forKey(Language.ANY, project))).containsExactly(OfKotlinForAnyLanguage::class.java)
    assertThat(classesOf(extension.forKey(language))).containsExactly(Plain::class.java, OfJava::class.java)
  }

  @Test
  fun `an explicit extension applies to every project`() {
    val explicit = Plain()
    extension.addExplicitExtension(language, explicit, disposable)
    installFilter(emptySet())
    assertThat(extension.allForLanguage(language, project)).containsExactly(explicit)
  }

  private fun installFilter(applicable: Set<PluginDescriptor>): FakeFilter {
    val filter = FakeFilter(project, applicable)
    ApplicationManager.getApplication().registerOrReplaceServiceInstance(TechnologyFilter::class.java, filter, disposable)
    return filter
  }

  /** A bean of [module], as the point creates it for a declaration. */
  private fun bean(language: Language, instance: Any, module: PluginDescriptor): LanguageExtensionPoint<Any> {
    return LanguageExtensionPoint(language.id, instance).also { it.pluginDescriptor = module }
  }

  private fun classesOf(extensions: List<Any>): List<Class<*>> = extensions.map { it.javaClass }
}

private const val POINT_NAME = "test.technologyAware.languageExtension"

/** Restricts [project] to the modules of [applicable]. */
private class FakeFilter(private val project: Project, @Volatile var applicable: Set<PluginDescriptor>) : TechnologyFilter {
  override fun isRestricted(project: Project): Boolean = project === this.project

  override fun isApplicable(module: PluginDescriptor, project: Project): Boolean = project !== this.project || module in applicable
}

private class Plain

private class OfJava

private class OfKotlinForAnyLanguage
