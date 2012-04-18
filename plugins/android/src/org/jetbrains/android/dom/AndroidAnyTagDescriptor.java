package org.jetbrains.android.dom;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidAnyTagDescriptor implements XmlElementDescriptor {
  private final XmlElementDescriptor myParentDescriptor;

  public AndroidAnyTagDescriptor(@NotNull XmlNSDescriptor nsDescriptor) {
    myParentDescriptor = new AnyXmlElementDescriptor(null, nsDescriptor);
  }

  @Override
  public String getQualifiedName() {
    return myParentDescriptor.getQualifiedName();
  }

  @Override
  public String getDefaultName() {
    return myParentDescriptor.getDefaultName();
  }

  @Override
  public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) {
    return myParentDescriptor.getElementsDescriptors(context);
  }

  @Override
  public XmlElementDescriptor getElementDescriptor(XmlTag childTag, XmlTag contextTag) {
    return new AndroidAnyTagDescriptor(getNSDescriptor());
  }

  @Override
  public XmlAttributeDescriptor[] getAttributesDescriptors(@Nullable XmlTag context) {
    return myParentDescriptor.getAttributesDescriptors(context);
  }

  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(@NonNls String attributeName, @Nullable XmlTag context) {
    return new AndroidAnyAttributeDescriptor(attributeName);
  }

  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attribute) {
    return new AndroidAnyAttributeDescriptor(attribute.getName());
  }

  @Override
  public XmlNSDescriptor getNSDescriptor() {
    return myParentDescriptor.getNSDescriptor();
  }

  @Override
  public XmlElementsGroup getTopGroup() {
    return myParentDescriptor.getTopGroup();
  }

  @Override
  public int getContentType() {
    return myParentDescriptor.getContentType();
  }

  @Override
  public String getDefaultValue() {
    return myParentDescriptor.getDefaultValue();
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
