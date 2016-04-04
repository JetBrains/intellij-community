/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.properties;

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
  @Nullable private String myKeyPrefix;
  @NonNls private static final String PREFIX_ATTR_NAME = "prefix";

  public PrefixBasedPropertyReference(String key, final PsiElement element, @Nullable final String bundleName, final boolean soft) {
    super(key, element, bundleName, soft);
  }

  @NotNull
  protected String getKeyText() {
    String keyText = super.getKeyText();
    final String keyPrefix = getKeyPrefix();
    if (keyPrefix != null) keyText = keyPrefix + keyText;
    return keyText;
  }

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

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final String keyPrefix = getKeyPrefix();
    if (keyPrefix != null) {
      if(newElementName.startsWith(keyPrefix)) {
        newElementName = newElementName.substring(keyPrefix.length());
      } else {
        throw new IncorrectOperationException(
          PropertiesBundle.message("rename.prefix.based.property.key.error.message",keyPrefix,getCanonicalText(),newElementName)
        );
      }
    }

    return super.handleElementRename(newElementName);
  }

  @Nullable
  private String getKeyPrefix() {
    if (!myPrefixEvaluated) {
      for(PsiElement curParent = PsiTreeUtil.getParentOfType(getElement().getParent().getParent(),XmlTag.class);
          curParent instanceof XmlTag;
          curParent = curParent.getParent()) {
        final XmlTag curParentTag = (XmlTag) curParent;

        if ("bundle".equals(curParentTag.getLocalName()) &&
            Arrays.binarySearch(XmlUtil.JSTL_FORMAT_URIS,curParentTag.getNamespace()) >= 0) {
          final String attributeValue = curParentTag.getAttributeValue(PREFIX_ATTR_NAME);

          if (attributeValue != null && attributeValue.length() > 0) {
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
