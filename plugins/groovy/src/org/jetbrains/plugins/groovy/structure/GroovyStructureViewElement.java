package org.jetbrains.plugins.groovy.structure;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Iconable;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 21.05.2007
 */

public class GroovyStructureViewElement implements StructureViewTreeElement {
  final private GroovyPsiElement myElement;

  public GroovyStructureViewElement(GroovyPsiElement element) {
    myElement = element;
  }

  public Object getValue() {
    return myElement;
  }

  public ItemPresentation getPresentation() {
    return new ItemPresentation() {

      public String getPresentableText() {
        return GroovyElementPresentation.getPresentableText(myElement);
      }

      @Nullable
      public String getLocationString() {
        return null;
      }

      @Nullable
      public Icon getIcon(boolean open) {
        return myElement.getIcon(Iconable.ICON_FLAG_OPEN);
      }

      @Nullable
      public TextAttributesKey getTextAttributesKey() {
        return null;
      }
    };
  }

  public void navigate(boolean b) {
    ((Navigatable) myElement).navigate(b);
  }

  public boolean canNavigate() {
    return ((Navigatable) myElement).canNavigate();
  }

  public boolean canNavigateToSource() {
    return ((Navigatable) myElement).canNavigateToSource();
  }

  public TreeElement[] getChildren() {
    List<GroovyStructureViewElement> children = new ArrayList<GroovyStructureViewElement>();

    if (myElement instanceof GroovyFileBase) {

      for (GrTopStatement topStatement : ((GroovyFileBase) myElement).getTopStatements()) {
        if (topStatement instanceof GrTypeDefinition || topStatement instanceof GrMethod) {
          addNewChild(children, topStatement);

        } else if (topStatement instanceof GrVariableDeclaration) {
            addVariables(children, ((GrVariableDeclaration) topStatement));
        }
      }

    } else if (myElement instanceof GrTypeDefinition) {  //adding statements for type definition
      GrMembersDeclaration[] declarations = ((GrTypeDefinition) myElement).getMemberDeclarations();

      if (declarations.length == 0) return children.toArray(StructureViewTreeElement.EMPTY_ARRAY);

      for (GrMembersDeclaration declaration : declarations) {
        if (declaration instanceof GrVariableDeclaration) {
          addVariables(children, (GrVariableDeclaration) declaration);
        } else {
          addNewChild(children, declaration);
        }
      }
    }

    return children.toArray(new StructureViewTreeElement[children.size()]);
  }

  private void addVariables(List<GroovyStructureViewElement> children, final GrVariableDeclaration variableDeclaration) {
    GrVariable[] variables = variableDeclaration.getVariables();

    for (final GrVariable variable : variables) {
      final Icon icon = variable.getIcon(Iconable.ICON_FLAG_OPEN);

      children.add(new GroovyStructureViewElement(variable) {
        public ItemPresentation getPresentation() {
          return new ItemPresentation() {
            public String getPresentableText() {
              return GroovyElementPresentation.getVariablePresentableText(variableDeclaration, variable.getNameIdentifierGroovy().getText());
            }

            @Nullable
            public String getLocationString() {
              return null;
            }

            @Nullable
            public Icon getIcon(boolean open) {
              return icon;
            }

            @Nullable
            public TextAttributesKey getTextAttributesKey() {
              return null;
            }
          };
        }
      });
    }
  }

  private void addNewChild(List<GroovyStructureViewElement> children, GroovyPsiElement element) {
    children.add(new GroovyStructureViewElement(element));
  }
}
