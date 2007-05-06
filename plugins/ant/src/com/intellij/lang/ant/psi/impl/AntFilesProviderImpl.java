/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFilesProvider;
import com.intellij.lang.ant.psi.impl.reference.AntRefIdReference;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLock;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: May 4, 2007
 */
public class AntFilesProviderImpl extends AntStructuredElementImpl implements AntFilesProvider {
  
  private AntPattern myPattern;
  
  public AntFilesProviderImpl(final AntElement parent, final XmlTag sourceElement) {
    super(parent, sourceElement);
  }

  public AntFilesProviderImpl(final AntElement parent, final XmlTag sourceElement, final AntTypeDefinition definition, @NonNls final String nameElementAttribute) {
    super(parent, sourceElement, definition, nameElementAttribute);
  }

  public AntFilesProviderImpl(final AntElement parent, final XmlTag sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
  }

  public void clearCaches() {
    synchronized (PsiLock.LOCK) {
      super.clearCaches();
      myPattern = null;
    }
  }

  @NotNull
  public final List<File> getFiles() {
    final AntFilesProvider referenced = getReferencedProvider();
    if (referenced != null) {
      return referenced.getFiles();
    }
    return getFiles(getPattern());
  }

  @NotNull
  protected List<File> getFiles(AntPattern pattern) {
    return Collections.emptyList();
  }
  
  protected AntPattern getPattern() {
    if (myPattern == null) {
      myPattern = AntPattern.create(this, shouldHonorDefaultExcludes(), matchPatternsCaseSensitive());
    }
    return myPattern;
  }

  
  private AntFilesProvider getReferencedProvider() {
    final PsiReference[] references = getReferences();
    for (PsiReference reference : references) {
      if (reference instanceof AntRefIdReference) {
        final PsiElement psiElement = reference.resolve();
        if (psiElement instanceof AntFilesProvider) {
          return (AntFilesProvider)psiElement;
        }
      }
    }
    return null;
  }
  
  private boolean shouldHonorDefaultExcludes() {
    @NonNls final String value = getSourceElement().getAttributeValue("defaultexcludes");
    return value == null || value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("true");
  }
  
  private boolean matchPatternsCaseSensitive() {
    @NonNls final String value = getSourceElement().getAttributeValue("casesensitive");
    return value == null || value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("true");
  }

  @Nullable
  protected File getCanonicalFile(final String path) {
    if (path == null) {
      return null;
    }
    try {
      final File file = new File(path);
      if (file.isAbsolute()) {
        return file.getCanonicalFile();
      }
      final String baseDir = computeAttributeValue("${" + AntFileImpl.BASEDIR_ATTR + "}");
      if (baseDir == null) {
        return null;
      }
      return new File(baseDir, path).getCanonicalFile();
    }
    catch (IOException e) {
      return null;
    }
  }
}
