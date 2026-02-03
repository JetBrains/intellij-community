// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a test assumption by Java version used to run the Gradle daemon.
 * <p>
 * <b>Variants:</b>
 * <ul>
 * <li> "11" -- run test with Java 11</li>
 * <li> "11+" -- run test with any Java version greater than 11 (included)</li>
 * <li> ">=11" -- run test with any Java version greater than 11 (included)</li>
 * <li> "<11" -- run test with any Java version prior to 11 (excluded)</li>
 * <li> "!11" -- run test with any Java version other than 11</li>
 * </ul>
 * <p>
 * <b>Deprecated notation:</b>
 * Version comparisons '>' and '<=' aren't logical. Changes can be made only in the specific version and present in the future.
 * We always can identify the version where new changes were made.
 * <ul>
 * <li> "<=11" -- run test with any Java version prior to 11 (included)</li>
 * <li> ">11" -- run test with any Java version greater than 11 (excluded)</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface TargetJavaVersion {

  String value();

  String reason();
}
