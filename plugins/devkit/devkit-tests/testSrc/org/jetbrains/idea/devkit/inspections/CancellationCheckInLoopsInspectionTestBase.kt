// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.fixtures.kotlin.KotlinTester
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixTestBase

abstract class CancellationCheckInLoopsInspectionTestBase : LightDevKitInspectionFixTestBase() {

  private val projectDescriptor = object : ProjectDescriptor(LanguageLevel.HIGHEST) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      KotlinTester.configureKotlinStdLib(model)
    }
  }

  override fun getProjectDescriptor() = projectDescriptor

  override fun setUp() {
    super.setUp()
    myFixture.addClass("""
      package com.intellij.util.concurrency.annotations; 
      
      public @interface RequiresReadLock { }
      """.trimIndent()
    )

    myFixture.addClass("""
      package inspections.cancellationCheckInLoops;
      
      public final class Foo {
        public static void doSomething() { }
      }
      """.trimIndent()
    )

    myFixture.addClass("""
      package com.intellij.openapi.progress;
      
      public abstract class ProgressManager {
        public static void checkCanceled() { }
      }
      """.trimIndent()
    )

    myFixture.addClass("""
      package com.intellij.util;
      
      @FunctionalInterface
      public interface Processor<T> {
        boolean process(T t);
      }
      """.trimIndent()
    )

    myFixture.addClass("""
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

    myFixture.enableInspections(CancellationCheckInLoopsInspection())
  }

}
