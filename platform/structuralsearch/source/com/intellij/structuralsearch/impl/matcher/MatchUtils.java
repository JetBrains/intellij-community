package com.intellij.structuralsearch.impl.matcher;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 24.12.2003
 * Time: 22:10:20
 * To change this template use Options | File Templates.
 */
public class MatchUtils {
  public static final String SPECIAL_CHARS = "*(){}[]^$\\.-|";

  public static final boolean compareWithNoDifferenceToPackage(String typeImage, String typeImage2) {
    return compareWithNoDifferenceToPackage(typeImage, typeImage2, false);
  }

  public static final boolean compareWithNoDifferenceToPackage(final String typeImage,@NonNls final String typeImage2, boolean ignoreCase) {
    if (typeImage == null || typeImage2 == null) return typeImage == typeImage2;
    final boolean endsWith = ignoreCase ? StringUtil.endsWithIgnoreCase(typeImage2, typeImage) : typeImage2.endsWith(typeImage);
    return endsWith && (
      typeImage.length() == typeImage2.length() ||
      typeImage2.charAt(typeImage2.length()-typeImage.length()-1)=='.' // package separator
    );
  }

  public static PsiElement getReferencedElement(final PsiElement element) {
    if (element instanceof PsiReference) {
      return ((PsiReference)element).resolve();
    }

    /*if (element instanceof PsiTypeElement) {
      PsiType type = ((PsiTypeElement)element).getType();

      if (type instanceof PsiArrayType) {
        type = ((PsiArrayType)type).getComponentType();
      }
      if (type instanceof PsiClassType) {
        return ((PsiClassType)type).resolve();
      }
      return null;
    }*/
    return element;
  }
}
