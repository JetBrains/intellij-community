// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.bucketing;

import com.intellij.TestCaseLoader;
import kotlin.text.StringsKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
@ApiStatus.Internal
public class TestsDurationBucketingScheme implements BucketingScheme {
  private List<TestsDurationBucketingUtils.BucketFilter> myBucketFilters;

  @Override
  public void initialize() {
    if (myBucketFilters != null && !myBucketFilters.isEmpty()) return;

    System.out.println("Tests duration bucketing initialization started ...");
    long start = System.nanoTime();

    var testCaseClasses = TestCaseLoader.loadClassesForWarmup();

    Set<String> classes = testCaseClasses.stream().map(Class::getName).collect(Collectors.toSet());

    myBucketFilters = TestsDurationBucketingUtils.calculateBucketFilters(TestCaseLoader.getCommonTestClassesFilterArgs(), classes);

    long durationNs = System.nanoTime() - start;
    System.out.printf("Tests duration bucketing initialization finished in %d ms.%n", TimeUnit.NANOSECONDS.toMillis(durationNs));
  }

  @Override
  public boolean matchesCurrentBucket(@NotNull String testIdentifier) {
    String packageName = StringsKt.substringBeforeLast(testIdentifier, '.', "");
    for (TestsDurationBucketingUtils.BucketFilter filter : myBucketFilters) {
      if (!filter.getPackageClasses().containsKey(packageName)) continue;
      TestsDurationBucketingUtils.BucketClassFilter classFilter = filter.getPackageClasses().get(packageName);
      if (classFilter == null) {
        // all classes in package
        return filter.getIndex() == TestCaseLoader.TEST_RUNNER_INDEX;
      }
      else if (classFilter.getClasses().contains(testIdentifier)) {
        return filter.getIndex() == TestCaseLoader.TEST_RUNNER_INDEX;
      }
      else {
        // see another bucket
      }
    }

    // None of the buckets contains this package, or that's a new class in the package, use an old scheme
    System.err.println("Fallback to default bucketing for: " + testIdentifier);
    return HashingBucketingScheme.matchesCurrentBucketViaHashing(testIdentifier);
  }
}
