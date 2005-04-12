package com.intellij.lang.properties;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2005
 * Time: 12:27:21 AM
 * To change this template use File | Settings | File Templates.
 */
public interface PropertiesElementTypes {
  IElementType FILE = new PropertiesElementType("FILE");
  IElementType PROPERTY = new PropertiesElementType("PROPERTY");
  IElementType KEY = new PropertiesElementType("PROPERTY_KEY");
  IElementType VALUE = new PropertiesElementType("PROPERTY_VALUE");
  IElementType KEY_VALUE_SEPARATOR = new PropertiesElementType("PROPERTY_KEY_VALUE_SEPARATOR");

  TokenSet PROPERTIES = TokenSet.create(new IElementType[]{PROPERTY});
}
