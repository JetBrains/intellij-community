package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.psi.*;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class AntImportImpl extends AntTaskImpl implements AntImport {

  public AntImportImpl(final AntElement parent, final XmlElement sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
    final AntFile imported = getFile();
    if (imported != null) {
      imported.getChildren();
      final AntFile file = getAntFile();
      final AntFileImpl importedFile = (AntFileImpl)imported;
      for (AntTypeDefinition def : importedFile.getBaseTypeDefinitions()) {
        if (file.getBaseTypeDefinition(def.getClassName()) == null) {
          registerCustomType(def);
        }
      }
      final AntProject project = getAntProject();
      final AntProjectImpl importedProject = (AntProjectImpl)importedFile.getAntProject();
      final AntElement[] importedChildren = importedProject.getChildren();
      if (importedChildren.length > 0) {
        AntStructuredElement firstChild = PsiTreeUtil.getChildOfType(importedProject, AntStructuredElement.class);
        if (firstChild != null) {
          // copy project ids
          for (String id : importedProject.getRefIds()) {
            project.registerRefId(id, firstChild.getElementByRefId(id));
          }
        }
      }
    }
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

  public boolean canContainFileReference() {
    return true;
  }

  @Nullable
  public String getFileName() {
    return getSourceElement().getAttributeValue("file");
  }

  public AntFile getFile() {
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
