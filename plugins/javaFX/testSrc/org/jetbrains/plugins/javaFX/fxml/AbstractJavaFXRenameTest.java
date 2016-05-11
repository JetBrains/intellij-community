package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;

/**
 * @author Pavel.Dolgov
 */
public abstract class AbstractJavaFXRenameTest extends AbstractJavaFXTestCase {
  protected void doRenameWithAutomaticRenamers(final String newName) {
    final PsiElement element = myFixture.getElementAtCaret();
    final RenameProcessor processor = new RenameProcessor(getProject(), element, newName, false, false);
    for (AutomaticRenamerFactory factory : Extensions.getExtensions(AutomaticRenamerFactory.EP_NAME)) {
      processor.addRenamerFactory(factory);
    }
    processor.run();
  }
}
