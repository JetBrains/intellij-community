package org.jetbrains.javafx.structure;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Iconable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.psi.*;

import javax.swing.*;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tree element presentation
 *
 * @author Alexey.Ivanov
 */
public class JavaFxStructureViewElement implements StructureViewTreeElement {
  private final JavaFxElement myElement;

  public JavaFxStructureViewElement(JavaFxElement element) {
    myElement = element;
  }

  public Object getValue() {
    return myElement;
  }

  public void navigate(boolean requestFocus) {
    myElement.navigate(requestFocus);
  }

  public boolean canNavigate() {
    return myElement.canNavigate();
  }

  public boolean canNavigateToSource() {
    return myElement.canNavigateToSource();
  }

  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        final StringBuilder name = new StringBuilder(myElement.getName());
        if (myElement instanceof JavaFxFunctionDefinition) {
          final JavaFxSignature signature = ((JavaFxFunctionDefinition)myElement).getSignature();
          if (signature != null) {
            final JavaFxParameterList parameterList = signature.getParameterList();
            if (parameterList != null) {
              name.append('(');
              final JavaFxParameter[] parameters = parameterList.getParameters();
              if (parameters.length != 0) {
                for (JavaFxParameter parameter : parameters) {
                  name.append(parameter.getText()).append(", ");
                }
                name.setLength(name.length() - 2);
              }
              name.append(')');
            }
          }
        }
        return name.toString();
      }

      @Nullable
      public TextAttributesKey getTextAttributesKey() {
        return null;
      }

      @Nullable
      public String getLocationString() {
        return null;
      }

      public Icon getIcon(boolean open) {
        return myElement.getIcon(Iconable.ICON_FLAG_OPEN);
      }
    };
  }

  public TreeElement[] getChildren() {
    final Set<JavaFxElement> childrenElements = new LinkedHashSet<JavaFxElement>();
    myElement.acceptChildren(new JavaFxElementVisitor() {
      @Override
      public void visitClassDefinition(JavaFxClassDefinition node) {
        childrenElements.add(node);
      }

      @Override
      public void visitFunctionDefinition(JavaFxFunctionDefinition node) {
        childrenElements.add(node);
      }

      @Override
      public void visitVariableDeclaration(JavaFxVariableDeclaration node) {
        childrenElements.add(node);
      }
    });

    final StructureViewTreeElement[] treeElements = new StructureViewTreeElement[childrenElements.size()];
    int i = 0;
    for (JavaFxElement element : childrenElements) {
      treeElements[i++] = new JavaFxStructureViewElement(element);
    }
    return treeElements;
  }
}
