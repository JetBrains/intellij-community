package org.jetbrains.javafx.refactoring;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.JavaFxFileType;
import org.jetbrains.javafx.lang.psi.JavaFxExpression;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxChangeUtil {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.javafx.refactoring.JavaFxChangeUtil");

  private JavaFxChangeUtil() {
  }

  public static PsiFile createDummyFile(final Project project, final String text) {
    return PsiFileFactory.getInstance(project).createFileFromText("dummy." + JavaFxFileType.INSTANCE.getDefaultExtension(), text);
  }

  @Nullable
  public static JavaFxExpression createExpressionFromText(final Project project, final String text) {
    final PsiFile dummyFile = createDummyFile(project, text);
    return (JavaFxExpression)dummyFile.getFirstChild();
  }
}
