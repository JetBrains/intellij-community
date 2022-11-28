// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.gradle.jvmcompat;

import com.intellij.openapi.application.ApplicationInfo
import org.jetbrains.plugins.gradle.jvmcompat.CompatibilityData

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate Gradle Compatibility Matrix" configuration instead
 */
 
internal val DEFAULT_DATA = CompatibilityData(
  listOf(
    VersionMapping("6-8", "INF-5.0", "https://docs.gradle.org/5.0/release-notes.html#potential-breaking-changes"),
    VersionMapping("8-9", "INF-5.1,7.2-INF", " Gradle older than 2.0 unofficially compatible with Java 8. Gradle from 5.1 to 7.1 and Java 8 aren't compatible: https://github.com/gradle/gradle/issues/8285"),
    VersionMapping("9-10", "4.3-INF"),
    VersionMapping("10-11", "4.7-INF"),
    VersionMapping("11-12", "5.0-INF"),
    VersionMapping("12-13", "5.4-INF"),
    VersionMapping("13-14", "6.0-INF"),
    VersionMapping("14-15", "6.3-INF"),
    VersionMapping("15-16", "6.7-INF", "Many builds might work with Java 15 but there are some known issues: https://github.com/gradle/gradle/issues/13532"),
    VersionMapping("16-17", "7.0-INF"),
    VersionMapping("17-18", "7.2-INF", "Gradle 7.2 and Java 17 are partially compatible: https://github.com/gradle/gradle/issues/16857"),
    VersionMapping("18-19", "7.5-INF"),
    VersionMapping("19-INF", "7.6-INF")
  ),
  listOf(
   "7",
    "8",
    "9",
    "10",
    "11",
    "12",
    "13",
    "14",
    "15",
    "16",
    "17",
    "18",
    "19"
  ),
  listOf(
   "3.0",
    "3.1",
    "3.2",
    "3.3",
    "3.4",
    "3.5",
    "4.0",
    "4.1",
    "4.2",
    "4.3",
    "4.4",
    "4.5",
    "4.5.1",
    "4.6",
    "4.7",
    "4.8",
    "4.9",
    "4.10",
    "4.10.3",
    "5.0",
    "5.1",
    "5.2",
    "5.3",
    "5.3.1",
    "5.4",
    "5.4.1",
    "5.5",
    "5.5.1",
    "5.6",
    "5.6.2",
    "6.0",
    "6.0.1",
    "6.1",
    "6.2",
    "6.3",
    "6.4",
    "6.5",
    "6.6",
    "6.7",
    "6.8",
    "6.8.3",
    "6.9",
    "7.0",
    "7.1",
    "7.2",
    "7.3",
    "7.4",
    "7.5",
    "7.5.1"
  )
);