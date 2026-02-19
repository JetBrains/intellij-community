// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.apache.commons.lang.text;

import org.apache.commons.lang3.text.StrLookup;
import org.apache.commons.lang3.text.StrMatcher;

import java.util.Map;

/**
 * @deprecated To continue using the functionality offered by commons-lang2,
 * please consider migrating to either the commons-lang3 or commons-text libraries and bundling them with your plugin.
 * Or consider using the corresponding API from IJ Platform.
 */
@Deprecated(forRemoval = true)
public final class StrSubstitutor extends org.apache.commons.lang3.text.StrSubstitutor {
  public StrSubstitutor() {
  }

  public <V> StrSubstitutor(Map<String, V> valueMap) {
    super(valueMap);
  }

  public <V> StrSubstitutor(Map<String, V> valueMap, String prefix, String suffix) {
    super(valueMap, prefix, suffix);
  }

  public <V> StrSubstitutor(Map<String, V> valueMap, String prefix, String suffix, char escape) {
    super(valueMap, prefix, suffix, escape);
  }

  public <V> StrSubstitutor(Map<String, V> valueMap, String prefix, String suffix, char escape, String valueDelimiter) {
    super(valueMap, prefix, suffix, escape, valueDelimiter);
  }

  public StrSubstitutor(StrLookup<?> variableResolver) {
    super(variableResolver);
  }

  public StrSubstitutor(StrLookup<?> variableResolver, String prefix, String suffix, char escape) {
    super(variableResolver, prefix, suffix, escape);
  }

  public StrSubstitutor(StrLookup<?> variableResolver, String prefix, String suffix, char escape, String valueDelimiter) {
    super(variableResolver, prefix, suffix, escape, valueDelimiter);
  }

  public StrSubstitutor(StrLookup<?> variableResolver, StrMatcher prefixMatcher, StrMatcher suffixMatcher, char escape) {
    super(variableResolver, prefixMatcher, suffixMatcher, escape);
  }

  public StrSubstitutor(StrLookup<?> variableResolver,
                        StrMatcher prefixMatcher,
                        StrMatcher suffixMatcher,
                        char escape,
                        StrMatcher valueDelimiterMatcher) {
    super(variableResolver, prefixMatcher, suffixMatcher, escape, valueDelimiterMatcher);
  }
}
