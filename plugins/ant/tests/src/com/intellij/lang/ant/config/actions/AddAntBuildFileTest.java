// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.TestDataProvider;
import com.intellij.testFramework.VfsTestUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AddAntBuildFileTest extends LightPlatformTestCase {
  @Test
  public void testEnabledOnAntProjectFile() {
    final VirtualFile file = VfsTestUtil.createFile(getSourceRoot(), "build.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project name="Test Ant Project" basedir=".">
      </project>
      """);
    final AnActionEvent event = doUpdate(file);

    assertTrue(event.getPresentation().isEnabled());
    assertTrue(event.getPresentation().isVisible());
    assertTrue(event.getPresentation().isEnabledAndVisible());
  }

  @Test
  public void testDisabledOnMavenProjectFile() {
    final VirtualFile file = VfsTestUtil.createFile(getSourceRoot(), "pom.xml", """
      <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.intellij.lang.ant.config.actions</groupId>
          <artifactId>test</artifactId>
          <version>0.1-SNAPSHOT</version>
      </project>
      """);
    final AnActionEvent event = doUpdate(file);

    assertFalse(event.getPresentation().isEnabled());
    assertFalse(event.getPresentation().isVisible());
    assertFalse(event.getPresentation().isEnabledAndVisible());
  }

  @Test
  public void testDisabledOnMavenProjectFileWithoutNamespace() {
    final VirtualFile file = VfsTestUtil.createFile(getSourceRoot(), "pom.xml", """
      <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.intellij.lang.ant.config.actions</groupId>
          <artifactId>test</artifactId>
          <version>0.1-SNAPSHOT</version>
      </project>
      """);
    final AnActionEvent event = doUpdate(file);

    assertFalse(event.getPresentation().isEnabled());
    assertFalse(event.getPresentation().isVisible());
    assertFalse(event.getPresentation().isEnabledAndVisible());
  }

  private @NotNull AnActionEvent doUpdate(VirtualFile file) {
    final AddAntBuildFile action = new AddAntBuildFile();
    final VirtualFile[] files = {file};
    final TestDataProvider provider = new TestDataProvider(getProject());
    final AnActionEvent event = AnActionEvent.createEvent(action, dataId -> {
      if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
        return files;
      }
      return provider.getData(dataId);
    }, null, ActionPlaces.PROJECT_VIEW_POPUP, ActionUiKind.NONE, null);
    action.update(event);
    return event;
  }
}