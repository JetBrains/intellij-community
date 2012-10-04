
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.impl.light.LightVariableBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * @author ilyas
 */
public class GrImplicitVariableImpl extends LightVariableBase implements GrImplicitVariable {
  public GrImplicitVariableImpl(PsiManager manager, PsiIdentifier nameIdentifier, @NotNull PsiType type, boolean writable, PsiElement scope) {
    super(manager, nameIdentifier, GroovyFileType.GROOVY_LANGUAGE, type, writable, scope);
  }

  public GrImplicitVariableImpl(PsiManager manager, @NonNls String name, @NonNls @NotNull String type, PsiElement scope) {
    this(manager, new GrLightIdentifier(manager, name), JavaPsiFacade.getElementFactory(manager.getProject()).
      createTypeFromText(type, scope), false, scope);
  }

  @Override
  protected PsiModifierList createModifierList() {
    return new GrLightModifierList(this);
  }

  public String toString() {
    return "Specific implicit variable: " + getName();
  }

  @Override
  @NotNull
  public GrLightModifierList getModifierList() {
    return (GrLightModifierList)myModifierList;
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return another == getNavigationElement() || super.isEquivalentTo(another);
  }

  protected static class GrLightIdentifier extends LightIdentifier {
    private String myTextInternal;

    public GrLightIdentifier(PsiManager manager, String name) {
      super(manager, name);
      myTextInternal = name;
    }

    public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
      myTextInternal = newElement.getText();
      return newElement;
    }

    public String getText() {
      return myTextInternal;
    }

    public PsiElement copy() {
      return new GrLightIdentifier(getManager(), myTextInternal);
    }
  }

}
