/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

/**
 * @author Eugene Zhuravlev
 *         Date: May 4, 2007
 */
public class AntFileListImpl extends AntFilesProviderImpl{
  public AntFileListImpl(final AntElement parent, final XmlTag sourceElement) {
    super(parent, sourceElement);
  }

  public AntFileListImpl(final AntElement parent, final XmlTag sourceElement, final AntTypeDefinition definition, @NonNls final String nameElementAttribute) {
    super(parent, sourceElement, definition, nameElementAttribute);
  }

  public AntFileListImpl(final AntElement parent, final XmlTag sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
  }

  protected AntPattern getPattern() {
    return null; // not available for this data type
  }

  @NotNull
  protected List<File> getFiles(final AntPattern pattern) {
    final File root = getCanonicalFile(computeAttributeValue(getSourceElement().getAttributeValue("dir")));
    if (root == null) {
      return Collections.emptyList();
    }

    final ArrayList<File> files = new ArrayList<File>();
      
    final String filenames = getSourceElement().getAttributeValue("files");
    if (filenames != null) {
      final StringTokenizer tokenizer = new StringTokenizer(filenames, ", \t\n\r\f", false);
      while (tokenizer.hasMoreTokens()) {
        files.add(new File(root, tokenizer.nextToken()));
      }
    }

    final AntElement[] children = getChildren();
    final AntTypeDefinition selfTypeDef = getTypeDefinition();
    if (selfTypeDef != null) {
      for (AntElement child : children) {
        if (child instanceof AntStructuredElement) {
          final AntStructuredElement se = (AntStructuredElement)child;
          final AntTypeDefinition typeDef = se.getTypeDefinition();
          if (typeDef != null && selfTypeDef.getNestedClassName(typeDef.getTypeId()) != null) {
            final String fileName = computeAttributeValue(se.getSourceElement().getAttributeValue("name"));
            if (fileName != null) {
              files.add(new File(root, fileName));
            }
          }
        }
      }
    }
    return files;
  }
}