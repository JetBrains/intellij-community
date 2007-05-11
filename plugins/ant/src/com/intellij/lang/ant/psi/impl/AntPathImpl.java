/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFilesProvider;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * @author Eugene Zhuravlev
 *         Date: May 5, 2007
 */
public class AntPathImpl extends AntFilesProviderImpl{
  public AntPathImpl(final AntElement parent, final XmlTag sourceElement) {
    super(parent, sourceElement);
  }

  public AntPathImpl(final AntElement parent, final XmlTag sourceElement, final AntTypeDefinition definition, @NonNls final String nameElementAttribute) {
    super(parent, sourceElement, definition, nameElementAttribute);
  }

  public AntPathImpl(final AntElement parent, final XmlTag sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
  }

  protected AntPattern getPattern() {
    return null; // not available
  }

  @NotNull
  protected List<File> getFiles(final AntPattern pattern) {
    final List<File> files = new ArrayList<File>();
    final File baseDir = getCanonicalFile(".");
    
    addLocation(baseDir, files, computeAttributeValue(getSourceElement().getAttributeValue("location")));
    
    final String pathString = computeAttributeValue(getSourceElement().getAttributeValue("path"));
    if (pathString != null) {
      final StringTokenizer tokenizer = new StringTokenizer(pathString, File.pathSeparator, false);
      while (tokenizer.hasMoreTokens()) {
        addLocation(baseDir, files, tokenizer.nextToken());
      }
    }

    final AntElement[] children = getChildren();
    for (AntElement child : children) {
      if (child instanceof AntFilesProvider) {
        files.addAll(((AntFilesProvider)child).getFiles());
      }
    }
    return files;
  }

  private static void addLocation(final File baseDir, final List<File> files, final String locationPath) {
    if (locationPath != null) {
      File file = new File(locationPath);
      if (file.isAbsolute()) {
        files.add(file);
      }
      else {
        files.add(new File(baseDir, locationPath));
      }
    }
  }

}
