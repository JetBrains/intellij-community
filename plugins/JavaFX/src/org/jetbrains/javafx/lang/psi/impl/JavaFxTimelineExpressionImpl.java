package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.psi.JavaFxTimelineExpression;
import org.jetbrains.javafx.lang.psi.impl.types.JavaFxTypeUtil;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   17:42:18
 */
public class JavaFxTimelineExpressionImpl extends JavaFxBaseElementImpl implements JavaFxTimelineExpression {
  public JavaFxTimelineExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public PsiType getType() {
    return JavaFxTypeUtil.getKeyValueClassType(getProject());
  }
}
