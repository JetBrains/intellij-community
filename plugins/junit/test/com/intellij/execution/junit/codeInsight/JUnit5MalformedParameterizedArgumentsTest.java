// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit.codeInsight;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_8;

public class JUnit5MalformedParameterizedArgumentsTest extends LightQuickFixParameterizedTestCase {

  @NotNull
  @Override
  protected String getTestDataPath() {
    String communityPath = PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/');
    return communityPath + "/plugins/junit/testData";
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/malformedParameterized/streamArgumentsMethodFix";
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new JUnit5MalformedParameterizedInspection()};
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    addEnvironment(getProject());
  }

  @Override
  protected void tearDown() throws Exception {
    cleanupEnvironment(getProject());
    super.tearDown();
  }

  private boolean cleanupEnvironment(Project project) {
    return WriteCommandAction.writeCommandAction(project).compute(() -> {
      try {
        getSourceRoot()
          .findChild("org")
          .delete(this);
      }
      catch (IOException ioe) {
        return false;
      }
      return true;
    });
  }

  private boolean addEnvironment(Project project) {
    boolean status = WriteCommandAction.writeCommandAction(project).compute(() -> {
      try {
        VirtualFile source = getSourceRoot()
          .createChildDirectory(this, "org")
          .createChildDirectory(this, "junit")
          .createChildDirectory(this, "jupiter")
          .createChildDirectory(this, "params")
          .createChildDirectory(this, "provider")
          .createChildData(this, "MethodSource.java");
        VfsUtil.saveText(source, "package org.junit.jupiter.params.provider;\n" +
                                 "public @interface MethodSource {String[] value();}");
      } catch (IOException ioe) {
        return false;
      }

      try {
        VirtualFile source = getSourceRoot()
          .createChildDirectory(this, "org")
          .createChildDirectory(this, "junit")
          .createChildDirectory(this, "jupiter")
          .createChildDirectory(this, "params")
          .createChildData(this, "ParameterizedTest.java");
        VfsUtil.saveText(source, "package org.junit.jupiter.params;\n" +
                                 "@org.junit.platform.commons.annotation.Testable\n" +
                                 "public @interface ParameterizedTest {}");
      } catch (IOException ioe) {
        return false;
      }

      try {
        VirtualFile source = getSourceRoot()
          .createChildDirectory(this, "org")
          .createChildDirectory(this, "junit")
          .createChildDirectory(this, "jupiter")
          .createChildDirectory(this, "params")
          .createChildDirectory(this, "provider")
          .createChildData(this, "Arguments.java");
        VfsUtil.saveText(source, "package org.junit.jupiter.params.provider;\n" +
                                 "public interface Arguments {static Arguments of(Object... arguments){}}\n");
      } catch (IOException ioe) {
        return false;
      }
      return true;
    });
    return status;
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}