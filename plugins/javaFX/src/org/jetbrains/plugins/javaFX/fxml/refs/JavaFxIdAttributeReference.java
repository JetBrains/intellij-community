package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.psi.PsiReference;

/**
 * @author Pavel.Dolgov
 */
public interface JavaFxIdAttributeReference extends PsiReference {
  boolean isBuiltIn();
}
