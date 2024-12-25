// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.bucketing;

import com.intellij.TestCaseLoader;
import com.intellij.nastradamus.NastradamusClient;
import com.intellij.teamcity.TeamCityClient;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
@ApiStatus.Internal
public class NastradamusBucketingScheme implements BucketingScheme {

  protected NastradamusClient myNastradamusClient;

  @Override
  public void initialize() {
    if (myNastradamusClient != null) return;
    myNastradamusClient = initNastradamus();
  }

  public @Nullable NastradamusClient getNastradamusClient() {
    return myNastradamusClient;
  }

  @Override
  public boolean matchesCurrentBucket(@NotNull String testIdentifier) {
    return matchesBucketViaNastradamus(testIdentifier);
  }

  protected boolean matchesBucketViaNastradamus(@NotNull String testIdentifier) {
    try {
      return myNastradamusClient.isClassInBucket(testIdentifier,
                                                 (testClassName) -> HashingBucketingScheme.matchesCurrentBucketViaHashing(testClassName));
    }
    catch (Exception e) {
      // if fails, just fallback to consistent hashing
      return HashingBucketingScheme.matchesCurrentBucketViaHashing(testIdentifier);
    }
  }

  private static synchronized NastradamusClient initNastradamus() {
    var testCaseClasses = TestCaseLoader.loadClassesForWarmup();
    NastradamusClient nastradamus = null;
    try {
      System.out.println("Caching data from Nastradamus and TeamCity ...");
      nastradamus = new NastradamusClient(
        new URI(System.getProperty("idea.nastradamus.url")).normalize(),
        testCaseClasses,
        new TeamCityClient()
      );
      nastradamus.getRankedClasses();
      System.out.println("Caching data from Nastradamus and TeamCity finished");
    }
    catch (Exception e) {
      System.err.println("Unexpected exception during Nastradamus client instance initialization");
      e.printStackTrace();
    }

    return nastradamus;
  }
}
