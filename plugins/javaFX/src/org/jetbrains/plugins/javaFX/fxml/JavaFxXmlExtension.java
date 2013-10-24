package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.xml.TagNameReference;
import com.intellij.xml.DefaultXmlExtension;
import org.jetbrains.plugins.javaFX.fxml.refs.JavaFxTagNameReference;

public class JavaFxXmlExtension extends DefaultXmlExtension {
  public boolean isAvailable(final PsiFile file) {
    return JavaFxFileTypeFactory.isFxml(file);
  }

  public TagNameReference createTagNameReference(final ASTNode nameElement, final boolean startTagFlag) {
    return new JavaFxTagNameReference(nameElement, startTagFlag);
  }
}
