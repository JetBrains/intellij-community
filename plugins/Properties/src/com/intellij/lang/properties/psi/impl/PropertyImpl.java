package com.intellij.lang.properties.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.lang.properties.PropertiesElementTypes;
import com.intellij.lang.properties.psi.PropertyKey;
import com.intellij.lang.properties.psi.PropertiesElementFactory;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.PropertyValue;
import com.intellij.psi.PsiElement;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 30, 2005
 * Time: 9:15:02 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertyImpl extends PropertiesElementImpl implements Property {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.properties.psi.impl.PropertyImpl");

  public PropertyImpl(final ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Property";
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    PropertyKey propertyKey = getKey();
    if (propertyKey == null) {
      propertyKey = PropertiesElementFactory.createKey(getProject(), name);
      getNode().addChild(propertyKey.getNode());
    }
    else {
      propertyKey.setName(name);
    }
    return this;
  }

  public String getName() {
    PropertyKey propertyKey = getKey();
    return propertyKey == null ? null : propertyKey.getName();
  }

  public PropertyKey getKey() {
    final ASTNode node = getNode().findChildByType(PropertiesElementTypes.KEY);
    if (node == null) {
      return null;
    }
    return (PropertyKey)node.getPsi();
  }

  public PropertyValue getValue() {
    final ASTNode node = getNode().findChildByType(PropertiesElementTypes.VALUE);
    if (node == null) {
      return null;
    }
    return (PropertyValue)node.getPsi();
  }

  public PsiElement getKeyValueSeparator() {
    final ASTNode node = getNode().findChildByType(PropertiesElementTypes.KEY_VALUE_SEPARATOR);
    if (node == null) {
      return null;
    }
    return node.getPsi();
  }

  public Icon getIcon(int flags) {
    return Icons.PROPERTY_ICON;
  }
}
