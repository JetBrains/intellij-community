package com.intellij.htmltools.xml.util;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.BasicAttributeValueReference;
import com.intellij.util.containers.JBIterable;
import com.intellij.xml.util.ColorSampleLookupValue;
import com.intellij.xml.util.UserColorLookup;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public final class ColorReference extends BasicAttributeValueReference {
  @NonNls
  static final Set<String> ourBgColorTagNames = Set.of("body", "td", "tr", "table", "th");

  @NonNls
  static final String BG_COLOR_ATTR_NAME = "bgcolor";

  @NonNls
  static final String COLOR_ATTR_NAME = "color";

  @NonNls
  static final String ALINK_ATTR_NAME = "alink";

  @NonNls
  static final String LINK_ATTR_NAME = "link";

  @NonNls
  static final String VLINK_ATTR_NAME = "vlink";

  @NonNls
  static final String TEXT_ATTR_NAME = "text";

  public ColorReference(final PsiElement element,int offset) {
    super(element, offset);
  }

  @Override
  @Nullable
  public PsiElement resolve() {
    return myElement; // important for color doc
  }

  @Override
  public LookupElement @NotNull [] getVariants() {
    return JBIterable
      .of(ColorSampleLookupValue.getColors())
      .map(ColorSampleLookupValue::toLookupElement)
      .append(new UserColorLookup())
      .toArray(LookupElement.EMPTY_ARRAY);
  }

  @Override
  public boolean isSoft() {
    return true;
  }

}
