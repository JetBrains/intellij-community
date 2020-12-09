// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.dom;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Pattern;

/**
* @author Eugene Zhuravlev
*/
public class PropertyExpander {
  private static final Pattern $$_PATTERN = Pattern.compile("\\$\\$");
  final List<PropertiesProvider> myProviders = new ArrayList<>();
  final Resolver myResolver;
  final Set<String> myNamesToSkip = new HashSet<>();
  private PropertyExpansionListener myPropertyExpansionListener;

  public interface PropertyExpansionListener {
    void onPropertyExpanded(String propName, String propValue);
  }

  public PropertyExpander(final @NotNull @NlsSafe String str) {
    this(str, Collections.emptySet());
  }

  private PropertyExpander(final @NotNull @NlsSafe String str, Set<@NlsSafe String> namesToSkip) {
    myResolver = new Resolver(str, namesToSkip);
    myNamesToSkip.addAll(namesToSkip);
  }

  /**
   * @param listener new listener implementation
   * @return previous listener
   */
  public PropertyExpansionListener setPropertyExpansionListener(PropertyExpansionListener listener) {
    final PropertyExpansionListener prevListener = myPropertyExpansionListener;
    myPropertyExpansionListener = listener;
    return prevListener;
  }

  public boolean hasPropertiesToExpand() {
    return myResolver.hasNext();
  }

  // true if should continue, false to stop
  public void acceptProvider(PropertiesProvider provider) {
    while (myResolver.hasNext()) {
      final String propName = myResolver.next();
      final String value = provider.getPropertyValue(propName);
      if (value != null) {
        myNamesToSkip.add(propName); // prevent infinite recursion
        final String propValue;
        if (provider instanceof PropertiesProvider.SkipPropertyExpansionInValues) {
          propValue = value;
        }
        else {
          final PropertyExpander propertyValueExpander = new PropertyExpander(value, myNamesToSkip);
          propertyValueExpander.setPropertyExpansionListener(myPropertyExpansionListener);
          if (propertyValueExpander.hasPropertiesToExpand()) {
            for (PropertiesProvider p : myProviders) {
              propertyValueExpander.acceptProvider(p);
              if (!propertyValueExpander.hasPropertiesToExpand()) {
                break;
              }
            }
            if (propertyValueExpander.hasPropertiesToExpand()) {
              propertyValueExpander.acceptProvider(provider);
            }
          }
          propValue = propertyValueExpander.getResult();
        }
        myResolver.replace(propValue);
        notifyPropertyExpanded(propName, propValue);
      }
    }
    myProviders.add(provider);
    myResolver.restart();
  }

  public void notifyPropertyExpanded(String propName, String propValue) {
    final PropertyExpansionListener listener = myPropertyExpansionListener;
    if (listener != null) {
      listener.onPropertyExpanded(propName, propValue);
    }
  }

  @NotNull
  public String getResult() {
    return myResolver.getResult();
  }


  private static final class Resolver implements Iterator<String> {
    private int myCurrentIndex = -1;
    private List<Pair<String /*property name without ${} characters*/, Integer /*offset of property occurrence including '$' char*/>> myPropertyNames;
    private final StringBuilder myBuilder;

    private Resolver(final String str, Set<String> namesToSkip) {
      myBuilder = new StringBuilder(str);
      int startProp = 0;
      while ((startProp = str.indexOf("${", startProp)) >= 0) {
        if (startProp > 0 && str.charAt(startProp - 1) == '$') {
          // the '$' is escaped
          startProp += 2;
          continue;
        }
        final int endProp = str.indexOf('}', startProp + 2);
        if (endProp <= startProp + 2) {
          startProp += 2;
          continue;
        }
        final String prop = str.substring(startProp + 2, endProp);
        if (!namesToSkip.contains(prop)) {
          if (myPropertyNames == null) {
            myPropertyNames = new ArrayList<>();
          }
          myPropertyNames.add(new Pair<>(prop, startProp));
        }
        startProp += 2;
      }
      if (myPropertyNames == null) {
        myPropertyNames = Collections.emptyList();
      }
    }

    void restart() {
      myCurrentIndex = -1;
    }

    void replace(String newValue) {
      final String name = getPropertyName(myCurrentIndex);
      final int shift = newValue.length() - name.length() - 3/*property beginning and ending symbols '${}'*/;
      // correct property offsets
      for (int idx = myCurrentIndex + 1; idx < myPropertyNames.size(); idx++) {
        final int currentOffset = getPropertyOffset(idx);
        setPropertyOffset(idx, currentOffset + shift);
      }
      final int offset = getPropertyOffset(myCurrentIndex);
      myBuilder.replace(offset, offset + name.length() + 3/*ending brace*/, newValue);
      myPropertyNames.remove(myCurrentIndex);
      myCurrentIndex--;
    }

    private String getPropertyName(int index) {
      return myPropertyNames.get(index).getFirst();
    }

    private int getPropertyOffset(int index) {
      return myPropertyNames.get(index).getSecond();
    }

    private void setPropertyOffset(int index, int value) {
      final Pair<String, Integer> pair = myPropertyNames.get(index);
      myPropertyNames.set(index, new Pair<>(pair.getFirst(), value));
    }

    String getResult() {
      final String value = myBuilder.toString();
      if (value.contains("$$")) {
        return $$_PATTERN.matcher(value).replaceAll("\\$");
      }
      return value;
    }

    @Override
    public boolean hasNext() {
      return (myCurrentIndex + 1) < myPropertyNames.size();
    }

    @Override
    public String next() {
      return getPropertyName(++myCurrentIndex);
    }

    @Override
    public void remove() {
      replace("");
    }
  }

}
