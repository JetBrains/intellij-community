// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml

import com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.packaging.artifacts.ArtifactManager
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.javaFX.packaging.JavaFxApplicationArtifactType
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import java.util.function.Function
import java.util.function.Supplier
import kotlin.time.Duration.Companion.seconds

/**
 * The fixture of the JavaFX tests. Create it with [javaFxFixture] in the project of [javaFxProjectFixture].
 *
 * The fixture enables [XmlUnresolvedReferenceInspection] and the inspections of the test class.
 * The members check the quick fixes of the test data and rename an element with the automatic renamers.
 * The companion holds the JavaFX descriptor and the helpers that add the JavaFX library to a module.
 */
class JavaFxFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
  private val hint: Function<String, String>?,
) {
  /**
   * Returns the name of the test method without the `test` prefix, as [LightJavaCodeInsightTestFixture.getTestName] does.
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String = fixture.getTestName(lowercaseFirstLetter)

  /**
   * Configures `<test name>.fxml` and runs the quick fix of the hint for [tagName].
   * Then it compares the result with `<test name>_after.fxml`. The quick fix and the check run on the EDT.
   */
  fun doLaunchQuickfixTest(tagName: String) {
    fixture.configureByFiles(getTestName(true) + ".fxml")
    val singleIntention = fixture.findSingleIntention(requireHint(tagName))
    assertNotNull(singleIntention)
    timeoutRunBlocking(60.seconds) {
      withContext(Dispatchers.EDT) {
        fixture.launchAction(singleIntention)
        fixture.checkResultByFile(getTestName(true) + "_after.fxml")
      }
    }
  }

  /**
   * Configures `<test name>.fxml` and checks that the quick fix of the hint for [tagName] is not available.
   */
  fun checkQuickFixNotAvailable(tagName: String) {
    fixture.configureByFiles(getTestName(true) + ".fxml")
    val intentions = fixture.availableIntentions
    val intention = CodeInsightTestUtil.findIntentionByText(intentions, requireHint(tagName))
    assertNull(intention)
  }

  /**
   * Renames the element at the caret to [newName] with all automatic renamers.
   */
  @RequiresEdt
  fun doRenameWithAutomaticRenamers(newName: String) {
    val element = fixture.elementAtCaret
    doRenameWithAutomaticRenamers(element, newName)
  }

  /**
   * Renames [elementAtCaret] to [newName] with all automatic renamers.
   */
  @RequiresEdt
  fun doRenameWithAutomaticRenamers(elementAtCaret: PsiElement, newName: String) {
    val processor = RenameProcessor(fixture.project, elementAtCaret, newName, false, false)
    for (factory in AutomaticRenamerFactory.EP_NAME.extensionList) {
      processor.addRenamerFactory(factory)
    }
    processor.run()
  }

  private fun requireHint(tagName: String): String {
    return checkNotNull(hint) { "Give the hint function to javaFxFixture" }.apply(tagName)
  }

  companion object {
    /**
     * The descriptor of the JavaFX tests. It adds the JavaFX library and a JavaFX application artifact.
     */
    @JvmField
    val JAVA_FX_DESCRIPTOR: DefaultLightProjectDescriptor = object : DefaultLightProjectDescriptor() {
      override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
        addJavaFxJarAsLibrary(model)
        ArtifactManager.getInstance(model.project).addArtifact("fake-javafx", JavaFxApplicationArtifactType.getInstance(), null)
        super.configureModule(module, model, contentEntry)
      }
    }

    @JvmStatic
    fun addJavaFxJarAsLibrary(module: Module) {
      ModuleRootModificationUtil.updateModel(module) { addJavaFxJarAsLibrary(it) }
    }

    @JvmStatic
    fun addJavaFxJarAsLibrary(model: ModifiableRootModel) {
      MavenDependencyUtil.addFromMaven(model, "org.openjfx:javafx-fxml:11.0.1")
    }
  }
}

/**
 * Creates the project of the JavaFX tests with [JavaFxFixture.JAVA_FX_DESCRIPTOR]. Declare it in a static field or in a companion object.
 */
fun javaFxProjectFixture(): TestFixture<LightJavaProject> = lightJavaProjectFixture(JavaFxFixture.JAVA_FX_DESCRIPTOR)

/**
 * Creates a [JavaFxFixture] for one test. Declare it as an instance field.
 *
 * [testDataPath] is the absolute test data path. When it is `null`, the fixture keeps the default path of [lightJavaCodeInsightFixture].
 * [inspections] gives the inspections of the test class. The fixture enables them after [XmlUnresolvedReferenceInspection].
 * [hint] gives the quick fix hint for a tag name to [JavaFxFixture.doLaunchQuickfixTest] and [JavaFxFixture.checkQuickFixNotAvailable].
 */
@JvmOverloads
fun javaFxFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
  inspections: Supplier<List<InspectionProfileEntry>>? = null,
  hint: Function<String, String>? = null,
): TestFixture<JavaFxFixture> = testFixture("javaFxFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture).init()
  if (testDataPath != null) {
    fixture.testDataPath = testDataPath
  }
  fixture.enableInspections(XmlUnresolvedReferenceInspection())
  if (inspections != null) {
    fixture.enableInspections(*inspections.get().toTypedArray())
  }
  initialized(JavaFxFixture(fixture, hint)) {}
}
