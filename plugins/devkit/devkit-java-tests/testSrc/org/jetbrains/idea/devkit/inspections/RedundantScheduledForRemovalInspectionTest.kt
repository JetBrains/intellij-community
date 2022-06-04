// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.internal.RedundantScheduledForRemovalAnnotationInspection

@TestDataPath("\$CONTENT_ROOT/testData/inspections/redundantScheduledForRemoval")
class RedundantScheduledForRemovalInspectionTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getBasePath() = "${DevkitJavaTestsUtil.TESTDATA_PATH}inspections/redundantScheduledForRemoval"

  override fun setUp() {
    super.setUp()
    LanguageLevelProjectExtension.getInstance(project).languageLevel = LanguageLevel.JDK_1_9
    myFixture.enableInspections(RedundantScheduledForRemovalAnnotationInspection())
    myFixture.addClass("""
      |package org.jetbrains.annotations; 
      |public class ApiStatus {  
      |  public @interface ScheduledForRemoval {
      |    String inVersion() default "";
      |  }
      |}""".trimMargin())
  }

  fun `test replace by attribute`() {
    myFixture.testHighlighting("ReplaceScheduledForRemovalByAttribute.java")
    myFixture.launchAction(myFixture.findSingleIntention("Replace"))
    myFixture.checkResultByFile("ReplaceScheduledForRemovalByAttribute_after.java")
  }

  fun `test remove`() {
    myFixture.testHighlighting("RemoveScheduledForRemoval.java")
    myFixture.launchAction(myFixture.findSingleIntention("Remove annotation"))
    myFixture.checkResultByFile("RemoveScheduledForRemoval_after.java")
  }

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return JAVA_9
  }
}