/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: May 4, 2007
 */
public class AntFileSetImpl extends AntFilesProviderImpl{
  public AntFileSetImpl(final AntElement parent, final XmlTag sourceElement) {
    super(parent, sourceElement);
  }

  public AntFileSetImpl(final AntElement parent, final XmlTag sourceElement, final AntTypeDefinition definition, @NonNls final String nameElementAttribute) {
    super(parent, sourceElement, definition, nameElementAttribute);
  }

  public AntFileSetImpl(final AntElement parent, final XmlTag sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
  }

  @NotNull
  protected List<File> getFiles(final AntPattern pattern) {
    final File root = getCanonicalFile(computeAttributeValue(getSourceElement().getAttributeValue("dir")));
    if (root != null) {
      final ArrayList<File> files = new ArrayList<File>();
      collectFiles(files, root, "", pattern);
      return files;
    }
    return Collections.emptyList();
  }
  
  private static void collectFiles(List<File> container, File from, String relativePath, final AntPattern pattern) {
    final File[] children = from.listFiles();
    if (children != null) {
      for (File child : children) {
        final String childPath = makePath(relativePath, child.getName());
        if (pattern.acceptPath(childPath)) {
          container.add(child);
        }
        if (child.isDirectory() && pattern.couldBeIncluded(childPath)) {
          collectFiles(container, child, childPath, pattern);
        }
      }
    }
  }

  private static String makePath(final String parentPath, final String name) {
    if (parentPath.length() == 0) {
      return name;
    }
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      return builder.append(parentPath).append("/").append(name).toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }
}
