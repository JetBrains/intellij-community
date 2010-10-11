package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.psi.JavaFxPsiUtil;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public abstract class JavaFxPresentableElementImpl<T extends StubElement> extends JavaFxBaseElementImpl<T> implements PsiNamedElement {
  public JavaFxPresentableElementImpl(@NotNull T stub, @NotNull IStubElementType nodeType) {
    super(stub, nodeType);
  }

  public JavaFxPresentableElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        final String name = getName();
        return (name != null) ? name : "<none>";
      }

      public String getLocationString() {
        return "(" + JavaFxPresentableElementImpl.this.getLocationString() + ")";
      }

      public Icon getIcon(boolean open) {
        return JavaFxPresentableElementImpl.this.getIcon(open ? ICON_FLAG_OPEN : ICON_FLAG_CLOSED);
      }

      public TextAttributesKey getTextAttributesKey() {
        return null;
      }
    };
  }

  @Override
  public int getTextOffset() {
    final ASTNode nameNode = getNameNode();
    if (nameNode != null) {
      return nameNode.getStartOffset();
    }
    return super.getTextOffset();
  }

  protected String getLocationString() {
    final String fileName = getContainingFile().getName();
    final int pos = fileName.lastIndexOf('.');
    return JavaFxPsiUtil.getPackageNameForElement(this) + "." + fileName.substring(0, pos);
  }
}
