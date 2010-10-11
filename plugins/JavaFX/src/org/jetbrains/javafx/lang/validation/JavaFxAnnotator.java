package org.jetbrains.javafx.lang.validation;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.JavaFxFileType;
import org.jetbrains.javafx.JavaFxLanguage;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxAnnotator implements Annotator {
  private static final Logger LOGGER = Logger.getInstance("#org.jetbrains.javafx.lang.validation.JavaFxAnnotator");
  private final List<JavaFxAnnotatingVisitor> myAnnotatingVisitors = new ArrayList<JavaFxAnnotatingVisitor>();

  public JavaFxAnnotator() {
    for (Class<? extends JavaFxAnnotatingVisitor> cls : ((JavaFxLanguage)JavaFxFileType.INSTANCE.getLanguage()).getAnnotators()) {
      JavaFxAnnotatingVisitor annotatingVisitor;
      try {
        annotatingVisitor = cls.newInstance();
      }
      catch (InstantiationException e) {
        LOGGER.error(e);
        continue;
      }
      catch (IllegalAccessException e) {
        LOGGER.error(e);
        continue;
      }
      myAnnotatingVisitors.add(annotatingVisitor);
    }
  }

  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    for(JavaFxAnnotatingVisitor visitor : myAnnotatingVisitors) {
      visitor.annotateElement(psiElement, holder);
    }
  }
}
