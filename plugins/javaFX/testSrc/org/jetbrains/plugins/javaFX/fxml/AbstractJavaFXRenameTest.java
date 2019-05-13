// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;

/**
 * @author Pavel.Dolgov
 */
public abstract class AbstractJavaFXRenameTest extends AbstractJavaFXTestCase {
  protected void doRenameWithAutomaticRenamers(final String newName) {
    final PsiElement element = myFixture.getElementAtCaret();
    doRenameWithAutomaticRenamers(element, newName);
  }

  protected void doRenameWithAutomaticRenamers(PsiElement elementAtCaret, String newName) {
    final RenameProcessor processor = new RenameProcessor(getProject(), elementAtCaret, newName, false, false);
    for (AutomaticRenamerFactory factory : AutomaticRenamerFactory.EP_NAME.getExtensionList()) {
      processor.addRenamerFactory(factory);
    }
    processor.run();
  }
}
