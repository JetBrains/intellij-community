/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * User: anna
 * Date: 11-Nov-2008
 */
package org.jetbrains.idea.eclipse;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Processor;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class EclipseProjectFinder implements EclipseXml {
  public static void findModuleRoots(final List<String> paths, final String rootPath, @Nullable Processor<String> progressUpdater) {
    if (progressUpdater != null) {
      progressUpdater.process(rootPath);
    }

    final String project = findProjectName(rootPath);
    if (project != null) {
      paths.add(rootPath);
    }

    final File root = new File(rootPath);
    if (root.isDirectory()) {
      final File[] files = root.listFiles();
      if (files != null) {
        for (File file : files) {
          findModuleRoots(paths, file.getPath(), progressUpdater);
        }
      }
    }
  }

  @Nullable
  public static String findProjectName(String rootPath) {
    String name = null;
    final File file = new File(rootPath, DOT_PROJECT_EXT);
    if (file.isFile()) {
      try {
        name = JDOMUtil.loadDocument(file).getRootElement().getChildText(NAME_TAG);
        if (StringUtil.isEmptyOrSpaces(name)) {
          return null;
        }
        name = name.replace("\n", " ").trim();
      }
      catch (JDOMException e) {
        return null;
      }
      catch (IOException e) {
        return null;
      }
    }
    return name;
  }

  @Nullable
  public static LinkedResource findLinkedResource(@NotNull String projectPath, @NotNull String relativePath) {
    String independentPath = FileUtil.toSystemIndependentName(relativePath);
    @NotNull String resourceName = independentPath;
    final int idx = independentPath.indexOf('/');
    if (idx != -1) {
      resourceName = independentPath.substring(0, idx);
    }
    final File file = new File(projectPath, DOT_PROJECT_EXT);
    if (file.isFile()) {
      try {
        for (Object o : JDOMUtil.loadDocument(file).getRootElement().getChildren(LINKED_RESOURCES)) {
          for (Object l : ((Element)o).getChildren(LINK)) {
            if (Comparing.strEqual(((Element)l).getChildText(NAME_TAG), resourceName)) {
              LinkedResource linkedResource = new LinkedResource();
              final String relativeToLinkedResourcePath =
                independentPath.length() > resourceName.length() ? independentPath.substring(resourceName.length()) : "";

              final Element locationURI = ((Element)l).getChild("locationURI");
              if (locationURI != null) {
                linkedResource.setURI(FileUtil.toSystemIndependentName(locationURI.getText()) + relativeToLinkedResourcePath);
              }

              final Element location = ((Element)l).getChild("location");
              if (location != null) {
                linkedResource.setLocation(FileUtil.toSystemIndependentName(location.getText()) + relativeToLinkedResourcePath);
              }
              return linkedResource;
            }
          }
        }
      }
      catch (Exception ignore) {
      }
    }
    return null;
  }

  public static class LinkedResource {
    private String myURI;
    private String myLocation;

    public String getVariableName() {
      final int idx = myURI.indexOf('/');
      return idx > -1 ? myURI.substring(0, idx) : myURI;
    }

    @Nullable
    public String getRelativeToVariablePath() {
      final int idx = myURI.indexOf('/');
      return idx > -1 && idx + 1 < myURI.length() ? myURI.substring(idx + 1) : null;
    }

    public boolean containsPathVariable() {
      return myURI != null;
    }

    public void setURI(String URI) {
      myURI = URI;
    }

    public String getLocation() {
      return myLocation;
    }

    public void setLocation(String location) {
      myLocation = location;
    }
  }
}