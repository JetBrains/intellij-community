/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFilesProvider;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

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
  protected List<File> getFiles(final AntPattern pattern, final Set<AntFilesProvider> processed) {
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