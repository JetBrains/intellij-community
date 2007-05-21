package org.jetbrains.plugins.groovy.structure;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Iconable;
import com.intellij.pom.Navigatable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclarations;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.ArrayList;

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
    final Icon icon = myElement.getIcon(Iconable.ICON_FLAG_OPEN);
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
        return icon;
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

    if (myElement instanceof GroovyFile) {
      GrTopStatement[] topStatements = ((GroovyFile) myElement).getTopStatements();

      for (GrTopStatement topStatement : topStatements) {
        if (topStatement instanceof GrTypeDefinition || topStatement instanceof GrMethod) {
          addNewChild(children, topStatement);

        } else if (topStatement instanceof GrVariableDeclarations) {
            addVariables(children, ((GrVariableDeclarations) topStatement));
        }
      }

    } else if (myElement instanceof GrTypeDefinition) {  //adding statements for type definition
      GrStatement[] statements = ((GrTypeDefinition) myElement).getStatements();

      for (GrStatement statement : statements) {
        if (statement instanceof GrVariableDeclarations) {
          addVariables(children, (GrVariableDeclarations) statement);
        } else {
          addNewChild(children, statement);
        }
      }
    }

    return children.toArray(StructureViewTreeElement.EMPTY_ARRAY);
  }

  private void addVariables(List<GroovyStructureViewElement> children, final GrVariableDeclarations variableDeclarations) {
    GrVariable[] grVariables = variableDeclarations.getVariables();

    for (final GrVariable variable : grVariables) {
      final Icon icon = variable.getIcon(Iconable.ICON_FLAG_OPEN);

      children.add(new GroovyStructureViewElement(variable) {
        public ItemPresentation getPresentation() {
          return new ItemPresentation() {
            public String getPresentableText() {
              return GroovyElementPresentation.getVariablePresentableText(variableDeclarations, variable.getNameIdentifierGroovy().getText());
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
