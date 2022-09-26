// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.TestCaseLoader;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class FairBucketingTest {

  private class Data {
    public String className;
    public int expectedBucket;

    public Data(String className, int expectedBucket) {
      this.className = className;
      this.expectedBucket = expectedBucket;
    }
  }

  private int totalBucketsCount = 5;
  private List<Data> testItems = Arrays.asList(
    new Data("com.intellij.integrationTests.smoke.idea.IdeaBuildOnJavaTest", 0),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaBuildOnKotlinTest", 1),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaCleanImportMavenProjectTest", 2),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaCreateAllServicesAndExtensionsTest", 3),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaGenerationTurbochargedSharedIndexesTest", 4),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaIndexingJavaProjectTest", 0),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaInspectionOnJavaProjectTest", 1),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaInspectionOnKotlinProjectTest", 2),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaInspectionOnSpringProjectTest", 3),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaJdkClassesCheckOnRedAfterRestartTest", 4),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaKotlinGradleOpenFilesTest", 0),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaOpenGradleProjectTest", 1),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaOpenMavenProjectTest", 2),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaReopenKotlinProjectFromCacheTest", 3),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaSpringCloudOpenFilesTest", 4)
  );

  @Test
  public void fairBucketingWorks() {
    for (int currentBucket = 0; currentBucket < totalBucketsCount; currentBucket++) {
      for (var testData : testItems) {
        var isMatchedBucket = testData.expectedBucket == currentBucket;

        Assert.assertEquals(String.format("Class `%s` should be in bucket %s", testData.className, currentBucket),
                            isMatchedBucket,
                            TestCaseLoader.matchesCurrentBucketFair(testData.className, totalBucketsCount, currentBucket));
      }
    }
  }

  @Test
  public void multipleFairBucketingInvocation() {
    fairBucketingWorks();
    fairBucketingWorks();
    fairBucketingWorks();
  }

  @Test
  public void initTestBucketsDoesNotThrow() {
    TestCaseLoader.initFairBuckets();
  }
}
