// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.PathUtil
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil

@TestDataPath($$"$CONTENT_ROOT/testData/inspections/jetBrainsExtensionsUsage")
internal class ExtensionsUsageInspectionTest : JavaCodeInsightFixtureTestCase() {

  override fun getBasePath(): String = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/jetBrainsExtensionsUsage"

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    moduleBuilder.addLibrary("annotations", PathUtil.getJarPathForClass(ApiStatus::class.java))
    moduleBuilder.addLibrary("platform-util", PathUtil.getJarPathForClass(XCollection::class.java))
  }

  override fun setUp() {
    super.setUp()
    myFixture.addClass("""
      package foo;
      import org.jetbrains.annotations.ApiStatus.Internal;

      @Internal
      public class MyInternalEP {
        @com.intellij.util.xmlb.annotations.Attribute
        @Internal public String internalAttribute;
      }""")
    myFixture.addClass("""
      package foo;
      import org.jetbrains.annotations.ApiStatus.Experimental;

      @Experimental
      public class MyExperimentalEP {
        @com.intellij.util.xmlb.annotations.Attribute
        @Experimental public String experimentalAttribute;
      }""")
    myFixture.addClass("""
      package foo;

      public class MyStableEP {
        @com.intellij.util.xmlb.annotations.Attribute
        public String stableAttribute;
      }""")
    myFixture.addClass("""
      package foo;
      import org.jetbrains.annotations.ApiStatus.Internal;

      @Internal
      public class MyInternalOuter {
        public static class MyNestedEP {
        }
      }""")
    myFixture.addFileToProject("internal/package-info.java", """
      @org.jetbrains.annotations.ApiStatus.Internal
      package internal;""".trimIndent())
    myFixture.addClass("""
      package internal;

      public class MyEpInInternalPackage {
      }""")
    myFixture.addClass("""
      package foo;
      import org.jetbrains.annotations.ApiStatus.Experimental;

      @Experimental
      public class MyExperimentalOuter {
        public static class MyNestedEP {
        }
      }""")
    myFixture.addFileToProject("experimental/package-info.java", """
      @org.jetbrains.annotations.ApiStatus.Experimental
      package experimental;""".trimIndent())
    myFixture.addClass("""
      package experimental;

      public class MyEpInExperimentalPackage {
      }""")
  }

  fun testInternalExtensionsUsage() {
    myFixture.enableInspections(JetBrainsInternalExtensionsUsageInspection().apply { ignoreApiDeclaredInThisProject = false })
    myFixture.testHighlighting(true, false, true, "internalExtensionsUsage.xml")
  }

  fun testUnstableExtensionsUsage() {
    myFixture.enableInspections(UnstableExtensionsUsageInspection().apply { ignoreApiDeclaredInThisProject = false })
    myFixture.testHighlighting(true, false, true, "unstableExtensionsUsage.xml")
  }

  fun testUnstableExtensionsUsageDeclaredInThisProject() {
    val inspection = UnstableExtensionsUsageInspection()
    inspection.ignoreApiDeclaredInThisProject = true
    myFixture.enableInspections(inspection)
    myFixture.testHighlighting(true, false, true, "unstableExtensionsUsageDeclaredInThisProject.xml")
  }
}
