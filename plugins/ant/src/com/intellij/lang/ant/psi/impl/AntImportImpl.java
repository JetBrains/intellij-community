package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntImport;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class AntImportImpl extends AntTaskImpl implements AntImport {

  public AntImportImpl(final AntElement parent, final XmlElement sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
  }

  public String toString() {
    @NonNls final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntImport[");
      builder.append(getFileName());
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public String getFileReferenceAttribute() {
    return "file";
  }

  @Nullable
  public String getFileName() {
    return computeAttributeValue(getSourceElement().getAttributeValue("file"));
  }

  public AntFile getImportedFile() {
    return getImportedFile(getFileName(), this);
  }

  public void clearCaches() {
    super.clearCaches();
    getAntFile().clearCaches();
  }

  @Nullable
  static AntFile getImportedFile(final String name, final AntStructuredElementImpl element) {
    if (name == null) return null;
    final PsiFile psiFile = element.findFileByName(name);
    if (psiFile != null) {
      if (psiFile instanceof XmlFile) {
        final FileViewProvider viewProvider = psiFile.getViewProvider();
        final VirtualFile file = psiFile.getVirtualFile();
        if (file != null) {
          AntSupport.markFileAsAntFile(file, viewProvider, true);
        }
        return (AntFile)viewProvider.getPsi(AntSupport.getLanguage());
      }
      if (psiFile instanceof AntFile) return (AntFile)psiFile;
    }
    return null;
  }
}
