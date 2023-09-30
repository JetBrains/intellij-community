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
  static final @NonNls Set<String> ourBgColorTagNames = Set.of("body", "td", "tr", "table", "th");

  static final @NonNls String BG_COLOR_ATTR_NAME = "bgcolor";

  static final @NonNls String COLOR_ATTR_NAME = "color";

  static final @NonNls String ALINK_ATTR_NAME = "alink";

  static final @NonNls String LINK_ATTR_NAME = "link";

  static final @NonNls String VLINK_ATTR_NAME = "vlink";

  static final @NonNls String TEXT_ATTR_NAME = "text";

  public ColorReference(final PsiElement element,int offset) {
    super(element, offset);
  }

  @Override
  public @Nullable PsiElement resolve() {
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
