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
import com.intellij.lang.ant.psi.impl.reference.AntRefIdReference;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLock;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlTag;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: May 4, 2007
 */
public class AntFilesProviderImpl extends AntStructuredElementImpl implements AntFilesProvider {
  
  private AntPattern myPattern;
  private volatile SoftReference<List<File>> myCachedFiles;
  
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
      clearCachedFiles();
      final AntElement parent = getAntParent();
      if (parent != null) {
        parent.clearCaches();
      }
    }
  }

  public void clearCachedFiles() {
    synchronized (PsiLock.LOCK) {
      myCachedFiles = null;
    }
  }

  @NotNull
  public final List<File> getFiles(final Set<AntFilesProvider> processed) {
    if (processed.contains(this)) {
      return Collections.emptyList();
    }
    List<File> result = myCachedFiles == null? null : myCachedFiles.get();
    if (result == null) {
      result = getFilesImpl(processed);
      myCachedFiles = new SoftReference<List<File>>(result);
    }
    return result;
  }

  private List<File> getFilesImpl(final Set<AntFilesProvider> processed) {
    processed.add(this);
    try {
      final AntFilesProvider referenced = getReferencedProvider();
      if (referenced != null) {
        return referenced.getFiles(processed);
      }
      return getFiles(getPattern(), processed);
    }
    finally {
      processed.remove(this);
    }
  }

  @NotNull
  protected List<File> getFiles(AntPattern pattern, final Set<AntFilesProvider> processed) {
    return Collections.emptyList();
  }
  
  protected AntPattern getPattern() {
    synchronized (PsiLock.LOCK) {
      if (myPattern == null) {
        myPattern = AntPattern.create(this, shouldHonorDefaultExcludes(), matchPatternsCaseSensitive());
      }
      return myPattern;
    }
  }

  
  @Nullable
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
