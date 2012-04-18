package org.jetbrains.android.dom;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.impl.schema.AnyXmlAttributeDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidAnyAttributeDescriptor implements XmlAttributeDescriptor {
  private final XmlAttributeDescriptor myParentDescriptor;

  public AndroidAnyAttributeDescriptor(@NotNull String attributeName) {
    myParentDescriptor = new AnyXmlAttributeDescriptor(attributeName);
  }

  @Override
  public boolean isRequired() {
    return myParentDescriptor.isRequired();
  }

  @Override
  public boolean isFixed() {
    return myParentDescriptor.isFixed();
  }

  @Override
  public boolean hasIdType() {
    return myParentDescriptor.hasIdType();
  }

  @Override
  public boolean hasIdRefType() {
    return myParentDescriptor.hasIdRefType();
  }

  @Override
  public String getDefaultValue() {
    return myParentDescriptor.getDefaultValue();
  }

  @Override
  public boolean isEnumerated() {
    return myParentDescriptor.isEnumerated();
  }

  @Override
  public String[] getEnumeratedValues() {
    return myParentDescriptor.getEnumeratedValues();
  }

  @Override
  public String validateValue(XmlElement context, String value) {
    return myParentDescriptor.validateValue(context, value);
  }

  @Override
  public PsiElement getDeclaration() {
    return myParentDescriptor.getDeclaration();
  }

  @Override
  public String getName(PsiElement context) {
    return myParentDescriptor.getName(context);
  }

  @Override
  public String getName() {
    return myParentDescriptor.getName();
  }

  @Override
  public void init(PsiElement element) {
    myParentDescriptor.init(element);
  }

  @Override
  public Object[] getDependences() {
    return myParentDescriptor.getDependences();
  }
}
