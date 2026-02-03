// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.bucketing

import org.junit.Assert
import org.junit.Test

class FairBucketingTest {
  private data class BucketedClass(var className: String, var expectedBucket: Int)

  private val totalBucketsCount = 5
  private val scheme = CyclicCounterBucketingScheme(totalBucketsCount)
  private val testClasses: List<BucketedClass> = listOf(
    BucketedClass("com.intellij.integrationTests.smoke.idea.IdeaBuildOnJavaTest", 0),
    BucketedClass("com.intellij.integrationTests.smoke.idea.IdeaBuildOnKotlinTest", 1),
    BucketedClass("com.intellij.integrationTests.smoke.idea.IdeaCleanImportMavenProjectTest", 2),
    BucketedClass("com.intellij.integrationTests.smoke.idea.IdeaCreateAllServicesAndExtensionsTest", 3),
    BucketedClass("com.intellij.integrationTests.smoke.idea.IdeaGenerationTurbochargedSharedIndexesTest", 4),
    BucketedClass("com.intellij.integrationTests.smoke.idea.IdeaIndexingJavaProjectTest", 0),
    BucketedClass("com.intellij.integrationTests.smoke.idea.IdeaInspectionOnJavaProjectTest", 1),
    BucketedClass("com.intellij.integrationTests.smoke.idea.IdeaInspectionOnKotlinProjectTest", 2),
    BucketedClass("com.intellij.integrationTests.smoke.idea.IdeaInspectionOnSpringProjectTest", 3),
    BucketedClass("com.intellij.integrationTests.smoke.idea.IdeaJdkClassesCheckOnRedAfterRestartTest", 4),
    BucketedClass("com.intellij.integrationTests.smoke.idea.IdeaKotlinGradleOpenFilesTest", 0),
    BucketedClass("com.intellij.integrationTests.smoke.idea.IdeaOpenGradleProjectTest", 1),
    BucketedClass("com.intellij.integrationTests.smoke.idea.IdeaOpenMavenProjectTest", 2),
    BucketedClass("com.intellij.integrationTests.smoke.idea.IdeaReopenKotlinProjectFromCacheTest", 3),
    BucketedClass("com.intellij.integrationTests.smoke.idea.IdeaSpringCloudOpenFilesTest", 4)
  )

  @Test
  fun fairBucketingWorks() {
    for (testData in testClasses) {
      Assert.assertEquals(String.format("Class `%s` should be in bucket %s", testData.className, testData.expectedBucket),
                          testData.expectedBucket,
                          scheme.getBucketNumber(testData.className, -1 /* used only for debug logs */))
    }
  }

  @Test
  fun multipleFairBucketingInvocation() {
    fairBucketingWorks()
    fairBucketingWorks()
    fairBucketingWorks()
  }

  @Test
  fun initTestBucketsDoesNotThrow() {
    CyclicCounterBucketingScheme(totalBucketsCount).initialize()
  }
}