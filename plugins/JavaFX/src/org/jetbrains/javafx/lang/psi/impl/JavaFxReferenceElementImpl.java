package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.*;
import org.jetbrains.javafx.lang.psi.impl.resolve.JavaFxImportReference;
import org.jetbrains.javafx.lang.psi.impl.resolve.JavaFxPackageReference;
import org.jetbrains.javafx.lang.psi.impl.resolve.JavaFxQualifiedReference;
import org.jetbrains.javafx.lang.psi.impl.resolve.JavaFxReference;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxReferenceElementImpl extends JavaFxBaseElementImpl implements JavaFxReferenceElement {
  public JavaFxReferenceElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public PsiReference getReference() {
    final JavaFxImportStatement importStatement = PsiTreeUtil.getParentOfType(this, JavaFxImportStatement.class);
    if (importStatement != null) {
      return new JavaFxImportReference(this, importStatement.isOnDemand());
    }
    final JavaFxPackageDefinition packageDefinition = PsiTreeUtil.getParentOfType(this, JavaFxPackageDefinition.class);
    if (packageDefinition != null) {
      return new JavaFxPackageReference(this);
    }
    final JavaFxExpression qualifier = getQualifier();
    return (qualifier == null) ? new JavaFxReference(this) : new JavaFxQualifiedReference(this, qualifier);
  }

  @Nullable
  @Override
  public JavaFxExpression getQualifier() {
    return (JavaFxExpression)childToPsi(JavaFxElementTypes.EXPRESSIONS);
  }

  public JavaFxQualifiedName getQualifiedName() {
    return JavaFxQualifiedName.fromString(getText());
  }

  @Override
  protected void acceptJavaFxVisitor(@NotNull JavaFxElementVisitor visitor) {
    visitor.visitReferenceElement(this);
  }
}
