package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.xml.TagNameReference;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.xml.DefaultXmlExtension;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.refs.JavaFxTagNameReference;

public class JavaFxXmlExtension extends DefaultXmlExtension {
  public boolean isAvailable(final PsiFile file) {
    return JavaFxFileTypeFactory.isFxml(file);
  }

  public TagNameReference createTagNameReference(final ASTNode nameElement, final boolean startTagFlag) {
    return new JavaFxTagNameReference(nameElement, startTagFlag);
  }

  @Nullable
  @Override
  public String[][] getNamespacesFromDocument(XmlDocument parent, boolean declarationsExist) {
    return XmlUtil.getDefaultNamespaces(parent);
  }
}
