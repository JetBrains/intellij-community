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
    new Data("com.intellij.integrationTests.smoke.idea.IdeaBuildOnJavaTest", 1),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaBuildOnKotlinTest", 2),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaCleanImportMavenProjectTest", 3),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaCreateAllServicesAndExtensionsTest", 4),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaGenerationTurbochargedSharedIndexesTest", 5),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaIndexingJavaProjectTest", 1),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaInspectionOnJavaProjectTest", 2),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaInspectionOnKotlinProjectTest", 3),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaInspectionOnSpringProjectTest", 4),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaJdkClassesCheckOnRedAfterRestartTest", 5),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaKotlinGradleOpenFilesTest", 1),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaOpenGradleProjectTest", 2),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaOpenMavenProjectTest", 3),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaReopenKotlinProjectFromCacheTest", 4),
    new Data("com.intellij.integrationTests.smoke.idea.IdeaSpringCloudOpenFilesTest", 5)
  );

  @Test
  public void fairBucketingWorks() {
    for (int currentBucket = 1; currentBucket <= totalBucketsCount; currentBucket++) {
      for (var testData : testItems) {
        var expectedMatch = testData.expectedBucket == currentBucket;
        var isMatchedCurrentBucket = TestCaseLoader.matchesCurrentBucketFair(testData.className, totalBucketsCount, currentBucket);

        var errorMessage = String.format("Class `%s` should be in bucket %s. Actual bucket is %s. Expected match: %s Actual match: %s",
                                         testData.className, testData.expectedBucket, currentBucket, expectedMatch, isMatchedCurrentBucket);
        Assert.assertEquals(errorMessage, expectedMatch, isMatchedCurrentBucket);
      }
    }
  }

  @Test
  public void multipleFairBucketingInvocation() {
    fairBucketingWorks();
    fairBucketingWorks();
    fairBucketingWorks();
  }
}
