/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ex.ProjectRoot;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.file.impl.FileManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;

/**
 * @author mike
 */
public class ProjectRootUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.impl.ProjectRootUtil");

  @NonNls public static final String SIMPLE_ROOT = "simple";
  @NonNls public static final String COMPOSITE_ROOT = "composite";
  /**
   * @deprecated
   */
  @NonNls public static final String JDK_ROOT = "jdk";
  /**
   * @deprecated
   */
  @NonNls public static final String OUTPUT_ROOT = "output";
  /**
   * @deprecated
   */
  @NonNls public static final String EXCLUDED_OUTPUT = "excludedOutput";
  /**
   * @deprecated
   */
  @NonNls public static final String LIBRARY_ROOT = "library";
  /**
   * @deprecated
   */
  @NonNls public static final String EJB_ROOT = "ejb";
  @NonNls private static final String ATTRIBUTE_TYPE = "type";
  @NonNls public static final String ELEMENT_ROOT = "root";

  private ProjectRootUtil() {
  }

  static ProjectRoot read(Element element)  {
    final String type = element.getAttributeValue(ATTRIBUTE_TYPE);

    if (type.equals(SIMPLE_ROOT)) {
      final SimpleProjectRoot root = new SimpleProjectRoot();
      root.readExternal(element);
      return root;
    }
    if (type.equals(COMPOSITE_ROOT)) {
      CompositeProjectRoot root = new CompositeProjectRoot();
      root.readExternal(element);
      return root;
    }
    throw new IllegalArgumentException("Wrong type: " + type);
  }

  static Element write(ProjectRoot projectRoot)  {
    Element element = new Element(ELEMENT_ROOT);
    if (projectRoot instanceof SimpleProjectRoot) {
      element.setAttribute(ATTRIBUTE_TYPE, SIMPLE_ROOT);
      ((SimpleProjectRoot)projectRoot).writeExternal(element);
    }
    else if (projectRoot instanceof CompositeProjectRoot) {
      element.setAttribute(ATTRIBUTE_TYPE, COMPOSITE_ROOT);
      ((CompositeProjectRoot)projectRoot).writeExternal(element);
    }
    else {
      throw new IllegalArgumentException("Wrong root: " + projectRoot);
    }

    return element;
  }

  public static PsiDirectory[] convertRoots(final Project project, VirtualFile[] roots) {
    return convertRoots(((PsiManagerImpl)PsiManager.getInstance(project)).getFileManager(), roots);
  }

  public static PsiDirectory[] convertRoots(final FileManager fileManager, VirtualFile[] roots) {
    ArrayList<PsiDirectory> dirs = new ArrayList<>();

    for (VirtualFile root : roots) {
      if (!root.isValid()) {
        LOG.error("Root " + root + " is not valid!");
      }
      PsiDirectory dir = fileManager.findDirectory(root);
      if (dir != null) {
        dirs.add(dir);
      }
    }

    return dirs.toArray(new PsiDirectory[dirs.size()]);
  }

  public static PsiDirectory[] getSourceRootDirectories(final Project project) {
    VirtualFile[] files = OrderEnumerator.orderEntries(project).sources().usingCache().getRoots();
    return convertRoots(project, files);
  }

  public static PsiDirectory[] getAllContentRoots(final Project project) {
    VirtualFile[] files = ProjectRootManager.getInstance(project).getContentRootsFromAllModules();
    return convertRoots(project, files);
  }
}
