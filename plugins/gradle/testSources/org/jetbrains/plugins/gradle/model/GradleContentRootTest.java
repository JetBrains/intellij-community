package org.jetbrains.plugins.gradle.model;

import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import org.junit.Test;

/**
 * @author Denis Zhdanov
 * @since 8/31/11 1:33 PM
 */
public class GradleContentRootTest {

  @Test(expected = IllegalArgumentException.class)
  public void pathOutOfContentRoot() {
    throw new IllegalArgumentException();
    // TODO den implement
//    ContentRootData contentRoot = new ContentRootData(new ModuleData("module", "."), "./my-content-root");
//    contentRoot.storePath(ExternalSystemSourceType.SOURCE, "./my-dir-out-of-content-root");
  }
}
