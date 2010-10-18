package org.jetbrains.android.dom.attrs;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author yole
 */
public class AttributeDefinition {
  private final String myName;
  private final Set<AttributeFormat> myFormats = EnumSet.noneOf(AttributeFormat.class);
  private final List<String> myValues = new ArrayList<String>();
                 
  public AttributeDefinition(@NotNull String name) {
    myName = name;
  }

  public AttributeDefinition(@NotNull String name, @NotNull Collection<AttributeFormat> formats) {
    myName = name;
    myFormats.addAll(formats);
  }

  public void addValue(@NotNull String name) {
    myValues.add(name);
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public Set<AttributeFormat> getFormats() {
    return Collections.unmodifiableSet(myFormats);
  }

  public void addFormats(@NotNull Collection<AttributeFormat> format) {
    myFormats.addAll(format);
  }

  @NotNull
  public String[] getValues() {
    return ArrayUtil.toStringArray(myValues);
  }

  @Override
  public String toString() {
    return myName + " [" + myFormats + ']';
  }
}
