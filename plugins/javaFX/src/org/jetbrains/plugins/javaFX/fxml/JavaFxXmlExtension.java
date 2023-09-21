// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.xml.TagNameReference;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.xml.DefaultXmlExtension;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.refs.JavaFxTagNameReference;

public final class JavaFxXmlExtension extends DefaultXmlExtension {
  @Override
  public boolean isAvailable(final PsiFile file) {
    return JavaFxFileTypeFactory.isFxml(file);
  }

  @Override
  public TagNameReference createTagNameReference(final ASTNode nameElement, final boolean startTagFlag) {
    return new JavaFxTagNameReference(nameElement, startTagFlag);
  }

  @Override
  public String[] @Nullable [] getNamespacesFromDocument(XmlDocument parent, boolean declarationsExist) {
    return XmlUtil.getDefaultNamespaces(parent);
  }
}
