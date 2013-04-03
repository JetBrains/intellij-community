package org.jetbrains.plugins.gradle.model;

import com.intellij.openapi.externalSystem.model.project.ExternalContentRoot;
import com.intellij.openapi.externalSystem.model.project.ExternalModule;
import com.intellij.openapi.externalSystem.model.project.SourceType;
import org.junit.Test;

/**
 * @author Denis Zhdanov
 * @since 8/31/11 1:33 PM
 */
public class GradleContentRootTest {

  @Test(expected = IllegalArgumentException.class)
  public void pathOutOfContentRoot() {
    ExternalContentRoot contentRoot = new ExternalContentRoot(new ExternalModule("module", "."), "./my-content-root");
    contentRoot.storePath(SourceType.SOURCE, "./my-dir-out-of-content-root");
  }
}
