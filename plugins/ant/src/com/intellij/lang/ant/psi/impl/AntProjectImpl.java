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

import com.intellij.lang.ant.AntElementRole;
import com.intellij.lang.ant.misc.PsiElementSetSpinAllocator;
import com.intellij.lang.ant.psi.*;
import com.intellij.lang.ant.psi.impl.reference.AntTargetReference;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@SuppressWarnings({"HardCodedStringLiteral"})
public class AntProjectImpl extends AntStructuredElementImpl implements AntProject {

  private volatile AntTarget[] myTargets;
  private volatile AntTarget[] myImportedTargets;
  private volatile List<AntFile> myImports;
  private AntFile[] myCachedImportsArray;

  private volatile Map<String, AntElement> myReferencedElements;
  private volatile String[] myRefIdsArray;

  public AntProjectImpl(final AntFileImpl parent, final XmlTag tag, final AntTypeDefinition projectDefinition) {
    super(parent, tag);
    myDefinition = projectDefinition;
  }

  public void acceptAntElementVisitor(@NotNull final AntElementVisitor visitor) {
    visitor.visitAntProject(this);
  }

  @NonNls
  public String toString() {
    final @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntProject[");
      final String name = getName();
      builder.append((name == null) ? "unnamed" : name);
      builder.append("]");
      if (getDescription() != null) {
        builder.append(" :");
        builder.append(getDescription());
      }
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public AntElementRole getRole() {
    return AntElementRole.PROJECT_ROLE;
  }

  public void clearCaches() {
    synchronized (PsiLock.LOCK) {
      super.clearCaches();
      myTargets = null;
      clearImports();
      myReferencedElements = null;
      myRefIdsArray = null;
      final AntFile antFile = getAntFile();
      if (antFile != null) {
        antFile.invalidateProperties();
      }
    }
  }

  public void clearImports() {
    synchronized (PsiLock.LOCK) {
      if (myBuildingImports == 0) {
        myImports = null;
      }
      myCachedImportsArray = null;
      myImportedTargets = null;
    }
  }

  @Nullable
  public String getBaseDir() {
    return computeAttributeValue(getSourceElement().getAttributeValue(AntFileImpl.BASEDIR_ATTR));
  }

  @Nullable
  public String getDescription() {
    final XmlTag tag = getSourceElement().findFirstSubTag(AntFileImpl.DESCRIPTION_ATTR);
    return tag != null ? tag.getValue().getTrimmedText() : null;
  }

  @NotNull
  public AntTarget[] getTargets() {
    synchronized (PsiLock.LOCK) {
      if (myTargets != null) {
        return myTargets;
      }
      //noinspection NonPrivateFieldAccessedInSynchronizedContext
      if (myInGettingChildren) {
        return AntTarget.EMPTY_ARRAY;
      }
      final List<AntTarget> targets = new ArrayList<AntTarget>();
      for (final AntElement child : getChildren()) {
        if (child instanceof AntTarget) {
          targets.add((AntTarget)child);
        }
      }
      final int size = targets.size();
      return myTargets = (size == 0) ? AntTarget.EMPTY_ARRAY : targets.toArray(new AntTarget[size]);
    }
  }

  @Nullable
  public AntTarget getDefaultTarget() {
    for (final PsiReference ref : getReferences()) {
      if (ref instanceof AntTargetReference) {
        return (AntTarget)ref.resolve();
      }
    }
    return null;
  }

  @NotNull
  public AntTarget[] getImportedTargets() {
    synchronized (PsiLock.LOCK) {
      if (myImportedTargets == null) {
        if (getImportedFiles().length == 0) {
          myImportedTargets = AntTarget.EMPTY_ARRAY;
        }
        else {
          final Set<PsiElement> targets = PsiElementSetSpinAllocator.alloc();
          try {
            final Set<PsiElement> elementsDepthStack = PsiElementSetSpinAllocator.alloc();
            try {
              getImportedTargets(this, targets, elementsDepthStack);
            }
            finally {
              PsiElementSetSpinAllocator.dispose(elementsDepthStack);
            }
            final int size = targets.size();
            myImportedTargets = (size == 0) ? AntTarget.EMPTY_ARRAY : targets.toArray(new AntTarget[size]);
          }
          finally {
            PsiElementSetSpinAllocator.dispose(targets);
          }
        }
      }
    }
    return myImportedTargets;
  }

  @Nullable
  public AntTarget getTarget(final String name) {
    for (final AntTarget target : getTargets()) {
      if (name.equals(target.getName())) {
        return target;
      }
    }
    return null;
  }

  /**
   * This method returns not only files that could be included via the <import>
   * task, but also a fake file which aggregates definitions resolved from all
   * entity references under the root tag.
   */
  private int myBuildingImports = 0;
  @NotNull
  public AntFile[] getImportedFiles() {
    synchronized (PsiLock.LOCK) {
      if (myImports == null) {
        // this is necessary to avoid recurrent getImportedFiles() and stack overflow
        myImports = new ArrayList<AntFile>();
        final XmlTag se = getSourceElement();
        final PsiFile psiFile = se.getContainingFile();
        final StringBuilder builder = StringBuilderSpinAllocator.alloc();
        myBuildingImports++;
        try {
          for (final XmlTag tag : se.getSubTags()) {
            // !!! a tag doesn't belong to the current file, so we decide it's resolved via an entity ref
            if (!psiFile.equals(tag.getContainingFile())) {
              buildTagText(tag, builder);
            }
            else if (AntFileImpl.IMPORT_TAG.equals(tag.getName())) {
              final String fileName = tag.getAttributeValue(AntFileImpl.FILE_ATTR);
              if (fileName != null) {
                final AntFile imported = AntImportImpl.getImportedFile(fileName, this);
                if (imported != null) {
                  addImportedFile(imported);
                }
              }
            }
          }
          if (builder.length() > 0) {
            builder.insert(0, "\">");
            final String baseDir = getBaseDir();
            if (baseDir != null && baseDir.length() > 0) {
              builder.insert(0, baseDir);
            }
            builder.insert(0, "<project basedir=\"");
            builder.append("</project>");
            final XmlFile xmlFile = (XmlFile)PsiFileFactory.getInstance(getProject())
              .createFileFromText("dummyEntities.xml", StdFileTypes.XML, builder.toString(), LocalTimeCounter.currentTime(), false, false);
            addImportedFile(new AntFileImpl(xmlFile.getViewProvider()) {
              //
              // this is necessary for computing file paths in tags resolved as entity references
              //
              public VirtualFile getContainingPath() {
                return AntProjectImpl.this.getAntFile().getContainingPath();
              }
            });
          }
        }
        finally {
          StringBuilderSpinAllocator.dispose(builder);
          myBuildingImports--;
          myCachedImportsArray = null;
          myImportedTargets = null;
        }
      }
      if (myCachedImportsArray == null) {
        myCachedImportsArray = myImports.toArray(new AntFile[myImports.size()]);
      }
      return myCachedImportsArray;
    }
  }

  private void addImportedFile(final AntFile imported) {
    try {
      myImports.add(imported);
    }
    finally {
      myCachedImportsArray = null;
    }
  }

  public void registerRefId(final String id, AntElement element) {
    synchronized (PsiLock.LOCK) {
      if (id == null || id.length() == 0) return;
      if (myReferencedElements == null) {
        myReferencedElements = new HashMap<String, AntElement>();
      }
      myReferencedElements.put(id, element);
    }
  }

  @Nullable
  public AntElement getElementByRefId(String refid) {
    synchronized (PsiLock.LOCK) {
      if (myReferencedElements == null) return null;
      refid = computeAttributeValue(refid);
      return myReferencedElements.get(refid);
    }
  }

  @NotNull
  public String[] getRefIds() {
    synchronized (PsiLock.LOCK) {
      if (myRefIdsArray == null) {
        if (myReferencedElements == null) {
          myRefIdsArray = ArrayUtil.EMPTY_STRING_ARRAY;
        }
        else {
          myRefIdsArray = myReferencedElements.keySet().toArray(new String[myReferencedElements.size()]);
        }
      }
      return myRefIdsArray;
    }
  }

  private static void getImportedTargets(final AntProject project,
                                         final Set<PsiElement> targets,
                                         final Set<PsiElement> elementsDepthStack) {
    if (elementsDepthStack.contains(project)) return;
    elementsDepthStack.add(project);
    try {
      for (final AntFile imported : project.getImportedFiles()) {
        final AntProject importedProject = imported.getAntProject();
        if (importedProject != null && !elementsDepthStack.contains(importedProject)) {
          for (final AntTarget target : importedProject.getTargets()) {
            targets.add(target);
          }
        }
        getImportedTargets(importedProject, targets, elementsDepthStack);
      }
    }
    finally {
      elementsDepthStack.remove(project);
    }
  }

  private static void buildTagText(final XmlTag tag, final StringBuilder builder) {
    //
    // this quite strange creation of the tag text is necessary since
    // tag.getText() removes whitespaces from source text
    //
    boolean firstChild = true;
    for (final PsiElement tagChild : tag.getChildren()) {
      if (!firstChild) {
        builder.append(' ');
      }
      if (tagChild instanceof XmlTag) {
        buildTagText((XmlTag)tagChild, builder);
      }
      else {
        builder.append(tagChild.getText());
      }
      firstChild = false;
    }
  }
}
