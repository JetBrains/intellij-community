/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.ant.dom;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomReferenceInjector;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
* @author Eugene Zhuravlev
*/
class AntReferenceInjector implements DomReferenceInjector {
  @Override
  public @Nullable @NlsSafe String resolveString(@Nullable @NonNls String unresolvedText, @NotNull ConvertContext context) {
    // todo: speed optimization: disable string resolution in places where it is not applicable
    if (unresolvedText == null) {
      return null;
    }
    final DomElement element = context.getInvocationElement();
    return AntStringResolver.computeString(element, unresolvedText);
  }

  @Override
  public PsiReference @NotNull [] inject(@Nullable String unresolvedText, @NotNull PsiElement element, @NotNull ConvertContext context) {
    if (element instanceof XmlAttributeValue xmlAttributeValue) {
      final List<PsiReference> refs = new ArrayList<>();
      addPropertyReferences(context, xmlAttributeValue, refs);
      addMacrodefParameterRefs(xmlAttributeValue, refs);
      return refs.toArray(PsiReference.EMPTY_ARRAY);
    }
    return PsiReference.EMPTY_ARRAY;
  }

  private static void addPropertyReferences(@NotNull ConvertContext context, final XmlAttributeValue xmlAttributeValue, final Collection<? super PsiReference> result) {
    final String value = xmlAttributeValue.getValue();
    final DomElement contextElement = context.getInvocationElement();
    
    final XmlAttribute attrib = PsiTreeUtil.getParentOfType(xmlAttributeValue, XmlAttribute.class);
    if (attrib != null) {
      final String name = attrib.getName();
      if ("if".equals(name) || "unless".equals(name)) {
        // special handling of if/unless attributes
        final AntDomPropertyReference ref = new AntDomPropertyReference(
          contextElement, xmlAttributeValue, ElementManipulators.getValueTextRange(xmlAttributeValue)
        );
        // in runtime, if execution reaches this task the property is defined since it is used in if-condition
        // so it is would be a mistake to highlight this as unresolved prop
        ref.setShouldBeSkippedByAnnotator(true);
        result.add(ref);
        return;
      }
    }

    final int valueBeginingOffset =
      Math.abs(xmlAttributeValue.getTextRange().getStartOffset() - xmlAttributeValue.getValueTextRange().getStartOffset());
    int startIndex;
    int endIndex = -1;
    while ((startIndex = value.indexOf("${", endIndex + 1)) > endIndex) {
      if (startIndex > 0 && value.charAt(startIndex - 1) == '$') {
        // the '$' is escaped
        endIndex = startIndex + 1;
        continue;
      }
      startIndex += 2;
      endIndex = startIndex;
      int nestedBrackets = 0;
      while (value.length() > endIndex) {
        final char ch = value.charAt(endIndex);
        if (ch == '}') {
          if (nestedBrackets == 0) {
            break;
          }
          --nestedBrackets;
        }
        else if (ch == '{') {
          ++nestedBrackets;
        }
        ++endIndex;
      }
      if (nestedBrackets > 0 || endIndex > value.length()) return;
      if (endIndex >= startIndex) {
        //final String propName = value.substring(startIndex, endIndex);
        //if (antFile.isEnvironmentProperty(propName) && antFile.getProperty(propName) == null) {
        //  continue;
        //}
        final AntDomPropertyReference ref = new AntDomPropertyReference(
          contextElement, xmlAttributeValue, new TextRange(valueBeginingOffset + startIndex, valueBeginingOffset + endIndex)
        );
        
        result.add(ref);
      }
      endIndex = startIndex;
    }
  }
  
  public static void addMacrodefParameterRefs(@NotNull XmlAttributeValue element, final Collection<? super PsiReference> refs) {
    final DomElement domElement = DomUtil.getDomElement(element);
    if (domElement == null) {
      return;
    }
    final AntDomMacroDef macrodef = domElement.getParentOfType(AntDomMacroDef.class, true);
    if (macrodef == null) {
      return;
    }
    final String text = ElementManipulators.getValueText(element);
    final int valueBeginingOffset = Math.abs(element.getTextRange().getStartOffset() - element.getValueTextRange().getStartOffset());
    int startIndex;
    int endIndex = -1;
    while ((startIndex = text.indexOf("@{", endIndex + 1)) > endIndex) {
      startIndex += 2;
      endIndex = startIndex;
      int nestedBrackets = 0;
      while (text.length() > endIndex) {
        final char ch = text.charAt(endIndex);
        if (ch == '}') {
          if (nestedBrackets == 0) {
            break;
          }
          --nestedBrackets;
        }
        else if (ch == '{') {
          ++nestedBrackets;
        }
        ++endIndex;
      }
      if(nestedBrackets > 0 || endIndex == text.length()) return;
      if (endIndex >= startIndex) {
        //final String name = text.substring(startIndex, endIndex);
         refs.add(new AntDomMacrodefAttributeReference(element, new TextRange(valueBeginingOffset + startIndex, valueBeginingOffset + endIndex)));
      }
      endIndex = startIndex; 
    }
  }


}
