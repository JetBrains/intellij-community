// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.properties.references.PropertyReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;

/**
 * @author Maxim.Mossienko
 */
public class PrefixBasedPropertyReference extends PropertyReference {
  private boolean myPrefixEvaluated;
  private boolean myDynamicPrefix;
  private @Nullable String myKeyPrefix;
  private static final @NonNls String PREFIX_ATTR_NAME = "prefix";

  public PrefixBasedPropertyReference(String key, final PsiElement element, final @Nullable String bundleName, final boolean soft) {
    super(key, element, bundleName, soft);
  }

  @Override
  protected @NotNull String getKeyText() {
    String keyText = super.getKeyText();
    final String keyPrefix = getKeyPrefix();
    if (keyPrefix != null) keyText = keyPrefix + keyText;
    return keyText;
  }

  @Override
  protected void addKey(Object property, Set<Object> variants) {
    String key = ((IProperty)property).getUnescapedKey();
    final String keyPrefix = getKeyPrefix();
    if (keyPrefix != null && key != null) {
      if (!key.startsWith(keyPrefix)) return;
      key = key.substring(keyPrefix.length());
      super.addKey(key, variants);
    }
    super.addKey(property, variants);
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    final String keyPrefix = getKeyPrefix();
    if (keyPrefix != null) {
      if(newElementName.startsWith(keyPrefix)) {
        newElementName = newElementName.substring(keyPrefix.length());
      } else {
        throw new IncorrectOperationException(
          JavaI18nBundle.message("rename.prefix.based.property.key.error.message",keyPrefix,getCanonicalText(),newElementName)
        );
      }
    }

    return super.handleElementRename(newElementName);
  }

  private @Nullable String getKeyPrefix() {
    if (!myPrefixEvaluated) {
      for(PsiElement curParent = PsiTreeUtil.getParentOfType(getElement().getParent().getParent(),XmlTag.class);
          curParent instanceof XmlTag curParentTag;
          curParent = curParent.getParent()) {

        if ("bundle".equals(curParentTag.getLocalName()) &&
            Arrays.binarySearch(XmlUtil.JSTL_FORMAT_URIS,curParentTag.getNamespace()) >= 0) {
          final String attributeValue = curParentTag.getAttributeValue(PREFIX_ATTR_NAME);

          if (attributeValue != null && !attributeValue.isEmpty()) {
            final XmlAttributeValue valueElement = curParentTag.getAttribute(PREFIX_ATTR_NAME, null).getValueElement();
            if (PropertiesReferenceProvider.isNonDynamicAttribute(valueElement)) {
              myKeyPrefix = attributeValue;
            }
            else {
              myDynamicPrefix = true;
            }
          }
          break;
        }
      }
      myPrefixEvaluated = true;
    }

    return myKeyPrefix;
  }

  public boolean isDynamicPrefix() {
    return myDynamicPrefix;
  }
}
