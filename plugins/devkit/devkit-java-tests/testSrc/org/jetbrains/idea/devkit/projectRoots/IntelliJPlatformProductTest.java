// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.projectRoots;

import com.intellij.testFramework.UsefulTestCase;

import static org.jetbrains.idea.devkit.projectRoots.IntelliJPlatformProduct.fromMavenCoordinates;

public class IntelliJPlatformProductTest extends UsefulTestCase {

  public void testFromMavenCoordinates() {
    assertSame(fromMavenCoordinates("com.jetbrains.intellij.idea", "ideaIU"), IntelliJPlatformProduct.IDEA_IU);
    assertSame(fromMavenCoordinates("com.jetbrains.intellij.idea", "ideaIC"), IntelliJPlatformProduct.IDEA_IC);
    assertSame(fromMavenCoordinates("com.jetbrains.intellij.phpstorm", "phpstorm"), IntelliJPlatformProduct.PHPSTORM);
    assertSame(fromMavenCoordinates("foo", "bar"), null);
  }
}
