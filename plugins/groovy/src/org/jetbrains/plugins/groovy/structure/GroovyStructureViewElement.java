package org.jetbrains.plugins.groovy.structure;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Iconable;
import com.intellij.pom.Navigatable;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
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
  final private SmartPsiElementPointer<GroovyPsiElement> myElementPointer;

  public GroovyStructureViewElement(GroovyPsiElement element) {
    myElementPointer = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
  }

  public Object getValue() {
    return myElementPointer;
  }

  public ItemPresentation getPresentation() {
    return new ItemPresentation() {

      public String getPresentableText() {
        GroovyPsiElement element = myElementPointer.getElement();
        return element == null ? "Invalid" : GroovyElementPresentation.getPresentableText(element);
      }

      @Nullable
      public String getLocationString() {
        return null;
      }

      @Nullable
      public Icon getIcon(boolean open) {
        GroovyPsiElement element = myElementPointer.getElement();
        return element == null ? null : element.getIcon(Iconable.ICON_FLAG_OPEN);
      }

      @Nullable
      public TextAttributesKey getTextAttributesKey() {
        return null;
      }
    };
  }

  public void navigate(boolean b) {
    GroovyPsiElement element = myElementPointer.getElement();
    if (element != null) {
      ((Navigatable) element).navigate(b);
    }
  }

  public boolean canNavigate() {
    GroovyPsiElement element = myElementPointer.getElement();
    return element != null && ((Navigatable) element).canNavigate();
  }

  public boolean canNavigateToSource() {
    GroovyPsiElement element = myElementPointer.getElement();
    return element != null && ((Navigatable) element).canNavigateToSource();
  }

  public TreeElement[] getChildren() {
    List<GroovyStructureViewElement> children = new ArrayList<GroovyStructureViewElement>();

    GroovyPsiElement element = myElementPointer.getElement();
    if (element instanceof GroovyFile) {
      GrTopStatement[] topStatements = ((GroovyFile) element).getTopStatements();

      for (GrTopStatement topStatement : topStatements) {
        if (topStatement instanceof GrTypeDefinition || topStatement instanceof GrMethod) {
          addNewChild(children, topStatement);

        } else if (topStatement instanceof GrVariableDeclaration) {
            addVariables(children, ((GrVariableDeclaration) topStatement));
        }
      }

    } else if (element instanceof GrTypeDefinition) {  //adding statements for type definition
      GrMembersDeclaration[] declarations = ((GrTypeDefinition) element).getMemberDeclarations();

      for (GrMembersDeclaration declaration : declarations) {
        if (declaration instanceof GrVariableDeclaration) {
          addVariables(children, (GrVariableDeclaration) declaration);
        } else {
          addNewChild(children, declaration);
        }
      }
    }

    return children.toArray(StructureViewTreeElement.EMPTY_ARRAY);
  }

  private void addVariables(List<GroovyStructureViewElement> children, final GrVariableDeclaration variableDeclaration) {
    GrVariable[] grVariables = variableDeclaration.getVariables();

    for (final GrVariable variable : grVariables) {
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
