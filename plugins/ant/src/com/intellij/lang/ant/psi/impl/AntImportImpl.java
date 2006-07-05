package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntImport;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
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
    @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
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
    return getSourceElement().getAttributeValue("file");
  }

  public AntFile getImportedFile() {
    final String name = getFileName();
    if (name == null) return null;
    PsiFile psiFile = findFileByName(name);
    if (psiFile != null) {
      if (psiFile instanceof AntFile) return (AntFile)psiFile;
      if (psiFile instanceof XmlFile) {
        return (AntFile)psiFile.getViewProvider().getPsi(AntSupport.getLanguage());
      }
    }
    return null;
  }
}
