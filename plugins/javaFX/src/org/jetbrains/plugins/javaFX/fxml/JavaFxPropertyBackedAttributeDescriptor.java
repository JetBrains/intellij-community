package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: 1/10/13
 */
public class JavaFxPropertyBackedAttributeDescriptor implements XmlAttributeDescriptor {
  private final String myName;
  private final PsiClass myPsiClass;

  public JavaFxPropertyBackedAttributeDescriptor(String name, PsiClass psiClass) {
    myName = name;
    myPsiClass = psiClass;
  }

  @Override
  public boolean isRequired() {
    return false;
  }

  @Override
  public boolean isFixed() {
    return false;
  }

  @Override
  public boolean hasIdType() {
    return false;
  }

  @Override
  public boolean hasIdRefType() {
    return false;
  }

  @Nullable
  @Override
  public String getDefaultValue() {
    return null;
  }

  @Override
  public boolean isEnumerated() {
    return false;
  }

  @Nullable
  @Override
  public String[] getEnumeratedValues() {
    return new String[0];
  }

  @Nullable
  @Override
  public String validateValue(XmlElement context, String value) {
    return null;
  }

  @Override
  public PsiElement getDeclaration() {
    return myPsiClass != null ? myPsiClass.findFieldByName(myName, true) : null;
  }

  @Override
  public String getName(PsiElement context) {
    return getName();
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public void init(PsiElement element) {}

  @Override
  public Object[] getDependences() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
