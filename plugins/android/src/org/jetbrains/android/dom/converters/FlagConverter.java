package org.jetbrains.android.dom.converters;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.ResolvingConverter;
import com.intellij.util.xml.converters.DelimitedListConverter;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.*;

/**
 * @author coyote
 */
public class FlagConverter extends DelimitedListConverter<String> {
  private final Set<String> myValues = new HashSet<String>();
  private final ResolvingConverter<String> additionalConverter;

  public FlagConverter(@Nullable ResolvingConverter<String> additionalConverter, @NotNull String... values) {
    super("|");
    this.additionalConverter = additionalConverter;
    Collections.addAll(myValues, values);
  }

  @NotNull
  @Override
  public Collection<? extends List<String>> getVariants(ConvertContext context) {
    if (additionalConverter == null) {
      return super.getVariants(context);
    }
    Collection<? extends String> variants = additionalConverter.getVariants(context);
    List<List<String>> result = new ArrayList<List<String>>();
    for (String variant : variants) {
      result.add(Arrays.asList(variant));
    }
    return result;
  }

  protected String convertString(final @Nullable String s, final ConvertContext context) {
    if (s == null || myValues.contains(s)) return s;
    return additionalConverter != null ? additionalConverter.fromString(s, context) : null;
  }

  protected String toString(final @Nullable String s) {
    return s;
  }

  protected Object[] getReferenceVariants(final ConvertContext context, final GenericDomValue<List<String>> value) {
    List<String> variants = new ArrayList<String>(myValues);
    filterVariants(variants, value);
    return ArrayUtil.toStringArray(variants);
  }

  @NotNull
  @Override
  protected PsiReference createPsiReference(PsiElement element,
                                            int start,
                                            int end,
                                            ConvertContext context,
                                            GenericDomValue<List<String>> value,
                                            boolean delimitersOnly) {
    return new MyPsiReference(element, getTextRange(value, start, end), context, value, delimitersOnly);
  }

  protected static TextRange getTextRange(GenericDomValue value, int start, int end) {
    if (value instanceof GenericAttributeValue) {
      return new TextRange(start, end);
    }
    TextRange tagRange = XmlTagUtil.getTrimmedValueRange(value.getXmlTag());
    return new TextRange(tagRange.getStartOffset() + start - 1, tagRange.getStartOffset() + end - 1);
  }

  protected PsiElement resolveReference(@Nullable final String s, final ConvertContext context) {
    return s == null ? null : context.getReferenceXmlElement();
  }

  protected String getUnresolvedMessage(final String value) {
    return MessageFormat.format(AndroidBundle.message("cannot.resolve.flag.error"), value);
  }
}
