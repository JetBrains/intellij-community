package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.misc.PsiElementSetSpinAllocator;
import com.intellij.lang.ant.psi.*;
import com.intellij.lang.ant.psi.impl.AntFileImpl;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Set;

public class AntFileReference extends AntGenericReference {

  private static final ReferenceType ourRefType = new ReferenceType(ReferenceType.FILE);

  public AntFileReference(final GenericReferenceProvider provider,
                          final AntStructuredElement antElement,
                          final String str,
                          final TextRange textRange) {
    super(provider, antElement, str, textRange, null);
  }

  public AntStructuredElement getElement() {
    return (AntStructuredElement)super.getElement();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final AntStructuredElement antElement = getElement();
    final XmlTag sourceElement = antElement.getSourceElement();
    if (sourceElement.getAttributeValue(AntFileImpl.FILE_ATTR) != null) {
      sourceElement.setAttribute(AntFileImpl.FILE_ATTR, newElementName);
    }
    return antElement;
  }

  @Nullable
  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    if (element instanceof PsiFile) {
      final PsiFile psiFile = (PsiFile)element;
      return handleElementRename(psiFile.getName());
    }
    throw new IncorrectOperationException("Can bind only to properties files.");
  }

  public static ReferenceType getReferenceType() {
    return ourRefType;
  }

  public ReferenceType getType() {
    return getReferenceType();
  }

  public ReferenceType getSoftenType() {
    return getReferenceType();
  }

  public PsiElement resolve() {
    final AntStructuredElement se = getElement();
    return se.findFileByName(getCanonicalText(), se instanceof AntImport);
  }

  public String getUnresolvedMessagePattern() {
    return AntBundle.message("file.doesnt.exist", getCanonicalRepresentationText());
  }

  public Object[] getVariants() {
    final AntStructuredElement se = getElement();
    if (!(se instanceof AntProperty)) return ourEmptyIntentions;
    final Set<PsiElement> propFiles = PsiElementSetSpinAllocator.alloc();
    try {
      final AntFile antFile = se.getAntFile();
      VirtualFile path = antFile.getContainingPath();
      if (path != null) {
        final AntProject project = antFile.getAntProject();
        final String baseDir = (project == null) ? null : project.getBaseDir();
        if (baseDir != null && baseDir.length() > 0) {
          path = LocalFileSystem.getInstance()
            .findFileByPath(new File(path.getPath(), baseDir).getAbsolutePath().replace(File.separatorChar, '/'));
        }
        if (path != null) {
          for (final VirtualFile file : path.getChildren()) {
            final PsiFile psiFile = se.getManager().findFile(file);
            if (psiFile instanceof PropertiesFile) {
              propFiles.add(psiFile);
            }
          }
        }
      }
      final int size = propFiles.size();
      return (size == 0) ? ourEmptyIntentions : propFiles.toArray(new PropertiesFile[size]);
    }
    finally {
      PsiElementSetSpinAllocator.dispose(propFiles);
    }
  }
}