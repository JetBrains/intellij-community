package org.jetbrains.android.dom.converters;

import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author coyote
 */
public class CompositeConverter extends ResolvingConverter<String> {
  private final List<ResolvingConverter<String>> converters = new ArrayList<ResolvingConverter<String>>();

  public void addConverter(@NotNull ResolvingConverter<String> converter) {
    converters.add(converter);
  }

  @NotNull
  public List<ResolvingConverter<String>> getConverters() {
    return converters;
  }

  public int size() {
    return converters.size();
  }

  @NotNull
  public Collection<String> getVariants(ConvertContext context) {
    List<String> variants = new ArrayList<String>();
    for (ResolvingConverter<String> converter : converters) {
      variants.addAll(converter.getVariants(context));
    }
    return variants;
  }

  public String fromString(@Nullable String s, ConvertContext context) {
    return s;
  }

  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }
}
