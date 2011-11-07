package org.jetbrains.plugins.gradle.model;

import org.junit.Test;

/**
 * @author Denis Zhdanov
 * @since 8/31/11 1:33 PM
 */
public class GradleContentRootTest {

  @Test(expected = IllegalArgumentException.class)
  public void pathOutOfContentRoot() {
    GradleContentRoot contentRoot = new GradleContentRoot("./my-content-root");
    contentRoot.storePath(SourceType.SOURCE, "./my-dir-out-of-content-root");
  }
}
