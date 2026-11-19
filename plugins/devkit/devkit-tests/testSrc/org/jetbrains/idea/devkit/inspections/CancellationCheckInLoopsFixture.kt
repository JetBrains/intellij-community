// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.ProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.kotlin.KotlinTester
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixture
import org.jetbrains.idea.devkit.inspections.quickfix.lightDevKitInspectionFixture

/**
 * Creates the project of a [CancellationCheckInLoopsInspection] test class: the latest language level and the Kotlin standard library.
 * Declare it in a static field or in a companion object.
 */
fun cancellationCheckInLoopsProjectFixture(): TestFixture<LightJavaProject> =
  lightJavaProjectFixture(object : ProjectDescriptor(LanguageLevel.HIGHEST) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      KotlinTester.configureKotlinStdLib(model)
    }
  })

/**
 * Creates a [LightDevKitInspectionFixture] for one test. Declare it as an instance field. Give it the project of [cancellationCheckInLoopsProjectFixture].
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 * The fixture enables [CancellationCheckInLoopsInspection]. The project has the stubs of `ProgressManager`, `ContainerUtil` and `RequiresReadLock`.
 */
@JvmOverloads
fun cancellationCheckInLoopsFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<LightDevKitInspectionFixture> = testFixture("cancellationCheckInLoopsFixture") {
  val devKit = lightDevKitInspectionFixture(projectFixture, fileExtension, basePath).init()
  val fixture = devKit.fixture
  fixture.addClass("""
    package com.intellij.util.concurrency.annotations; 
    
    public @interface RequiresReadLock { }
    """.trimIndent()
  )
  fixture.addClass("""
    package inspections.cancellationCheckInLoops;
    
    public final class Foo {
      public static void doSomething() { }
    }
    """.trimIndent()
  )
  fixture.addClass("""
    package com.intellij.openapi.progress;
    
    public abstract class ProgressManager {
      public static void checkCanceled() { }
    }
    """.trimIndent()
  )
  fixture.addClass("""
    package com.intellij.util;
    
    @FunctionalInterface
    public interface Processor<T> {
      boolean process(T t);
    }
    """.trimIndent()
  )
  fixture.addClass("""
    package com.intellij.util.containers;
    
    import com.intellij.util.Processor;
    
    public final class ContainerUtil {
      public static <T> boolean process(Iterable<? extends T> iterable, Processor<? super T> processor) {return true;}
      public static <T> boolean process(List<? extends T> list, Processor<? super T> processor) {return true;}
      public static <T> boolean process(T [] iterable, Processor<? super T> processor) {return true;}
      public static <T> boolean process(Iterator<? extends T> iterator, Processor<? super T> processor) {return true;}
    }
    """.trimIndent()
  )
  fixture.enableInspections(CancellationCheckInLoopsInspection())
  initialized(devKit) {}
}
