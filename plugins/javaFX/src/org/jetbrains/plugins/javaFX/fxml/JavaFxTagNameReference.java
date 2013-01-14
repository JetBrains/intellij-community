package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.xml.TagNameReference;

/**
 * User: anna
 * Date: 1/8/13
 */
public class JavaFxTagNameReference extends TagNameReference {
  public JavaFxTagNameReference(ASTNode element, boolean startTagFlag) {
    super(element, startTagFlag);
  }
}
