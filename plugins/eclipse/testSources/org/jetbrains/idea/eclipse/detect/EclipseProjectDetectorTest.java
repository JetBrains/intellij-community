// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.detect;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

public class EclipseProjectDetectorTest {

  @Test
  public void testWorkspaces() throws IOException {
    String[] workspaces = EclipseProjectDetector.getWorkspaces(
      "RECENT_WORKSPACES=~/eclipse-workspace/multiproject-workspace\\n~/eclipse-workspace/spring-crud-app\\n~/eclipse-workspace\n" +
      "RECENT_WORKSPACES_PROTOCOL=3\n" +
      "SHOW_RECENT_WORKSPACES=true\n" +
      "SHOW_WORKSPACE_SELECTION_DIALOG=true\n" +
      "eclipse.preferences.version=1");
    Assertions.assertEquals(3, workspaces.length);
    Assertions.assertEquals("~/eclipse-workspace/multiproject-workspace", workspaces[0]);
  }

  @Test
  public void testParseOomphLocations() throws Exception {
    List<String> locations = EclipseProjectDetector.parseOomphLocations("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                                                        "<setup:LocationCatalog\n" +
                                                                        "    xmi:version=\"2.0\"\n" +
                                                                        "    xmlns:xmi=\"http://www.omg.org/XMI\"\n" +
                                                                        "    xmlns:setup=\"http://www.eclipse.org/oomph/setup/1.0\">\n" +
                                                                        "  <installation>\n" +
                                                                        "    <key href=\"file:/C:/Users/bond/eclipse/java-2021-03/eclipse/configuration/org.eclipse.oomph.setup/installation.setup#/\"/>\n" +
                                                                        "    <value href=\"file:/C:/Users/bond/eclipse-workspace11/.metadata/.plugins/org.eclipse.oomph.setup/workspace.setup#/\"/>\n" +
                                                                        "    <value href=\"file:/C:/Users/bond/eclipse-workspace/.metadata/.plugins/org.eclipse.oomph.setup/workspace.setup#/\"/>\n" +
                                                                        "  </installation>\n" +
                                                                        "  <workspace>\n" +
                                                                        "    <key href=\"file:/C:/Users/bond/eclipse-workspace11/.metadata/.plugins/org.eclipse.oomph.setup/workspace.setup#/\"/>\n" +
                                                                        "    <value href=\"file:/C:/Users/bond/eclipse/java-2021-03/eclipse/configuration/org.eclipse.oomph.setup/installation.setup#/\"/>\n" +
                                                                        "  </workspace>\n" +
                                                                        "  <workspace>\n" +
                                                                        "    <key href=\"file:/C:/Users/bond/eclipse-workspace/.metadata/.plugins/org.eclipse.oomph.setup/workspace.setup#/\"/>\n" +
                                                                        "    <value href=\"file:/C:/Users/bond/eclipse/java-2021-03/eclipse/configuration/org.eclipse.oomph.setup/installation.setup#/\"/>\n" +
                                                                        "  </workspace>\n" +
                                                                        "</setup:LocationCatalog>");
    Assertions.assertEquals(2, locations.size());
    Assertions.assertEquals("file:/C:/Users/bond/eclipse-workspace11", locations.get(0));
  }
}
