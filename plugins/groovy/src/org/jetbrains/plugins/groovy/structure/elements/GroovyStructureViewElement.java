package org.jetbrains.plugins.groovy.structure.elements;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 21.05.2007
 */

abstract public class GroovyStructureViewElement implements StructureViewTreeElement {
  final protected PsiElement myElement;

  public GroovyStructureViewElement(PsiElement element) {
    myElement = element;
  }

  public Object getValue() {
    return myElement.isValid() ? myElement : null;
  }

//  public ItemPresentation getPresentation() {
//    return new ItemPresentation() {
//
//      public String getPresentableText() {
//        return GroovyElementPresentation.getPresentableText(myElement);
//      }
//
//      @Nullable
//      public String getLocationString() {
//        return null;
//      }
//
//      @Nullable
//      public Icon getIcon(boolean open) {
//        return myElement.isValid() ? myElement.getIcon(Iconable.ICON_FLAG_OPEN) : null;
//      }
//
//      @Nullable
//      public TextAttributesKey getTextAttributesKey() {
//        return null;
//      }
//    };
//  }

  public void navigate(boolean b) {
    ((Navigatable) myElement).navigate(b);
  }

  public boolean canNavigate() {
    return ((Navigatable) myElement).canNavigate();
  }

  public boolean canNavigateToSource() {
    return ((Navigatable) myElement).canNavigateToSource();
  }

  /*public TreeElement[] getChildren() {
    List<GroovyStructureViewElement> children = new ArrayList<GroovyStructureViewElement>();

    if (myElement instanceof GroovyFileBase) {

      for (GrTopStatement topStatement : ((GroovyFileBase) myElement).getTopStatements()) {
        if (topStatement instanceof GrTypeDefinition) {
          children.add(new GroovyTypeDefinitionStructureViewElement(topStatement, false));
        } else if (topStatement instanceof GrMethod) {
          children.add(new GroovyMethodStructureViewElement(topStatement));

        } else if (topStatement instanceof GrVariableDeclaration) {
          addVariables(children, ((GrVariableDeclaration) topStatement));
        }
      }

    } else if (myElement instanceof GrTypeDefinition) {
      //adding statements for type definition
      final GrTypeDefinition typeDefinition = (GrTypeDefinition) myElement;
      children.add(new GroovyTypeDefinitionStructureViewElement(typeDefinition, false));
    }

    return children.toArray(new StructureViewTreeElement[children.size()]);
  }*/

}
