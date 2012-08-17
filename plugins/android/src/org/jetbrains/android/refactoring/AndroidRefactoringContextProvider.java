package org.jetbrains.android.refactoring;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public interface AndroidRefactoringContextProvider {
  ExtensionPointName<AndroidRefactoringContextProvider> EP_NAME =
    ExtensionPointName.create("org.jetbrains.android.refactoringContextProvider");

  @NotNull
  XmlTag[] getComponentTags(@NotNull DataContext dataContext);
}
