package org.jetbrains.android.dom.converters;

import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Eugene.Kudelevsky
 */
public class IntegerConverter extends ResolvingConverter<String> {
  public static final IntegerConverter INSTANCE = new IntegerConverter();

  private IntegerConverter() {
  }

  @NotNull
  @Override
  public Collection<? extends String> getVariants(ConvertContext context) {
    return Collections.emptyList();
  }

  @Override
  public String fromString(@Nullable @NonNls String s, ConvertContext context) {
    if (s == null || AndroidResourceUtil.isIdDeclaration(s) || AndroidResourceUtil.isIdReference(s)) {
      return s;
    }
    try {
      Integer.parseInt(s);
    }
    catch (NumberFormatException e) {
      return null;
    }
    return s;
  }

  @Override
  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }
}
