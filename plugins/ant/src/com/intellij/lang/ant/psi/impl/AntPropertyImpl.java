package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProperty;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class AntPropertyImpl extends AntTaskImpl implements AntProperty {

  public AntPropertyImpl(final AntElement parent, final XmlElement sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
  }

  public String toString() {
    @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntProperty: [");
      if (getName() != null) {
        builder.append(getName());
        builder.append(" = ");
        builder.append(getValue());
      }
      else {
        final String propFile = getFileName();
        if (propFile != null) {
          builder.append("file: ");
          builder.append(propFile);
        }
      }
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  @Nullable
  public String getName() {
    return getSourceElement().getAttributeValue("name");
  }

  public PsiElement setName(final String name) throws IncorrectOperationException {
    final XmlTag sourceElement = getSourceElement();
    if (sourceElement.getAttribute("name", null) == null) {
      throw new IncorrectOperationException("Can't set name of an unnamed property");
    }
    sourceElement.setAttribute("name", name);
    subtreeChanged();
    return this;
  }

  @Nullable
  public String getValue() {
    final XmlTag sourceElement = getSourceElement();
    if (sourceElement.getAttributeValue("name") != null) {
      String value = sourceElement.getAttributeValue("value");
      if (value != null) {
        return value;
      }
      value = sourceElement.getAttributeValue("location");
      if (value != null) {
        String baseDir = getAntProject().getBaseDir();
        if (baseDir != null) {
          return new File(baseDir, value).getAbsolutePath();
        }
        return value;
      }
    }
    return null;
  }

  public void setValue(final String value) throws IncorrectOperationException {
    final XmlTag sourceElement = getSourceElement();
    if (sourceElement.getAttribute("name", null) == null) {
      throw new IncorrectOperationException("Can't set value of an unnamed property");
    }
    sourceElement.setAttribute("value", value);
    subtreeChanged();
  }

  @Nullable
  public String getFileName() {
    return getSourceElement().getAttributeValue("file");
  }

  @Nullable
  public PropertiesFile getPropertiesFile() {
    final String filename = getFileName();
    if (filename == null) return null;
    AntFileImpl antFile = PsiTreeUtil.getParentOfType(this, AntFileImpl.class);
    if (antFile == null) return null;
    VirtualFile vFile = antFile.getVirtualFile();
    if (vFile == null) return null;
    vFile = vFile.getParent();
    if (vFile == null) return null;
    final File file = new File(vFile.getPath(), filename);
    vFile = LocalFileSystem.getInstance().findFileByPath(file.getAbsolutePath().replace(File.separatorChar, '/'));
    if (vFile == null) return null;
    return (PropertiesFile)antFile.getViewProvider().getManager().findFile(vFile);
  }

  public void setPropertiesFile(final String name) throws IncorrectOperationException {
    getSourceElement().setAttribute("file", name);
    subtreeChanged();
  }

  public PsiElement getNavigationElement() {
    final XmlTag sourceElement = getSourceElement();
    final XmlAttribute attribute = sourceElement.getAttribute("name", null);
    if (attribute != null) {
      return attribute.getValueElement();
    }
    return this;
  }
}
