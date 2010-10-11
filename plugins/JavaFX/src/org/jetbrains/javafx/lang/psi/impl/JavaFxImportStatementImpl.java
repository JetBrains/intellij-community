package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.lexer.JavaFxTokenTypes;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxImportStatement;
import org.jetbrains.javafx.lang.psi.JavaFxReferenceElement;
import org.jetbrains.javafx.lang.psi.impl.resolve.JavaFxResolveProcessor;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   16:42:12
 */
public class JavaFxImportStatementImpl extends JavaFxBaseElementImpl implements JavaFxImportStatement {
  public JavaFxImportStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Nullable
  @Override
  public JavaFxReferenceElement getImportReference() {
    final ASTNode node = getNode().findChildByType(JavaFxElementTypes.REFERENCE_ELEMENT);
    if (node == null) {
      return null;
    }
    return (JavaFxReferenceElement)node.getPsi();
  }

  @Override
  public boolean isOnDemand() {
    return getNode().findChildByType(JavaFxTokenTypes.MULT) != null;
  }

  @Override
  public JavaFxQualifiedName getQualifiedName() {
    final JavaFxReferenceElement importReference = getImportReference();
    if (importReference == null) {
      return null;
    }
    return importReference.getQualifiedName();
  }

  @Override
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull final ResolveState state,
                                     final PsiElement lastParent,
                                     @NotNull final PsiElement place) {
    final PsiFile containingFile = getContainingFile();
    if (place.getContainingFile() != containingFile) {
      return true;
    }
    final JavaFxReferenceElement importReferenceElement = getImportReference();
    if (importReferenceElement != null) {
      final PsiReference importReference = importReferenceElement.getReference();
      if (importReference != null) {
        if (!isOnDemand()) {
          final NameHint nameHint = processor.getHint(NameHint.KEY);
          String name = null;
          if (nameHint != null) {
            name = nameHint.getName(state);
          }
          else if (processor instanceof JavaFxResolveProcessor) {
            name = ((JavaFxResolveProcessor)processor).getName();
          }
          if (name != null) {
            if (StringUtil.endsWith(getText(), name)) {
              final PsiElement resolveResult = importReference.resolve();
              if (resolveResult != null && resolveResult != containingFile) {
                return resolveResult instanceof PsiFile
                       ? resolveResult.processDeclarations(processor, state, this, place)
                       : processor.execute(resolveResult, state);
              }
            }
          }
        }
        else {
          final PsiElement resolveResult = importReference.resolve();
          if (resolveResult instanceof PsiPackage || resolveResult instanceof PsiFile && resolveResult != containingFile) {
            return resolveResult.processDeclarations(processor, state, this, place);
          }
        }
      }
    }
    return true;
  }
}
