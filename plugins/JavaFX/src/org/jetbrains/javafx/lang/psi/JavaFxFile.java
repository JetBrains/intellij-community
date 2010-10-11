package org.jetbrains.javafx.lang.psi;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public interface JavaFxFile extends JavaFxElement, PsiFile {
  @Nullable
  JavaFxPackageDefinition getPackageDefinition();

  @NotNull
  JavaFxImportList[] getImportLists();

  @NotNull
  JavaFxElement[] getDefinitions();
}
