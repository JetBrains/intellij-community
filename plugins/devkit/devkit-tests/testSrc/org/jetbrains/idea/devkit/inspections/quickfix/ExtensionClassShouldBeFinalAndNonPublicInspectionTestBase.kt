// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import org.jetbrains.idea.devkit.inspections.ExtensionClassShouldBeFinalAndNonPublicInspection

abstract class ExtensionClassShouldBeFinalAndNonPublicInspectionTestBase : LightDevKitInspectionFixTestBase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(ExtensionClassShouldBeFinalAndNonPublicInspection())
    myFixture.addClass(
      """
      package org.jetbrains.annotations;

      public @interface VisibleForTesting { }
    """)
  }

  protected fun addServiceDescriptorClass() {
    myFixture.addClass(
      """
      package com.intellij.util.xmlb.annotations;
      
      public @interface Attribute {}
      """
    )

    myFixture.addClass(
      """
      package com.intellij.openapi.components;
      
      public final class ServiceDescriptor {
      
        @com.intellij.util.xmlb.annotations.Attribute
        public final String serviceImplementation;

      }
      """
    )
  }
}
