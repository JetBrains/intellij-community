// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import junit.framework.TestCase;

import java.io.IOException;

public class EclipseProjectDetectorTest extends TestCase {

  public void testWorkspaces() throws IOException {
    String[] workspaces = EclipseProjectsDetector.getWorkspaces(
      "RECENT_WORKSPACES=~/eclipse-workspace/multiproject-workspace\\n~/eclipse-workspace/spring-crud-app\\n~/eclipse-workspace\n" +
      "RECENT_WORKSPACES_PROTOCOL=3\n" +
      "SHOW_RECENT_WORKSPACES=true\n" +
      "SHOW_WORKSPACE_SELECTION_DIALOG=true\n" +
      "eclipse.preferences.version=1");
    assertEquals(3, workspaces.length);
    assertEquals("~/eclipse-workspace/multiproject-workspace", workspaces[0]);
  }
}
