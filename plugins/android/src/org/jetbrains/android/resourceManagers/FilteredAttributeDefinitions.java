package org.jetbrains.android.resourceManagers;

import com.intellij.util.containers.HashSet;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.attrs.StyleableDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class FilteredAttributeDefinitions implements AttributeDefinitions {
  private final AttributeDefinitions myWrappee;

  protected FilteredAttributeDefinitions(@NotNull AttributeDefinitions wrappee) {
    myWrappee = wrappee;
  }

  protected abstract boolean isAttributeAcceptable(@NotNull String name);

  @Nullable
  @Override
  public StyleableDefinition getStyleableByName(@NotNull String name) {
    final StyleableDefinition styleable = myWrappee.getStyleableByName(name);
    return styleable != null ? new MyStyleableDefinition(styleable) : null;
  }

  @NotNull
  @Override
  public Set<String> getAttributeNames() {
    final Set<String> result = new HashSet<String>();

    for (String name : myWrappee.getAttributeNames()) {
      if (isAttributeAcceptable(name)) {
        result.add(name);
      }
    }
    return result;
  }

  @Nullable
  @Override
  public AttributeDefinition getAttrDefByName(@NotNull String name) {
    return isAttributeAcceptable(name) ? myWrappee.getAttrDefByName(name) : null;
  }

  @NotNull
  @Override
  public StyleableDefinition[] getStateStyleables() {
    final StyleableDefinition[] styleables = myWrappee.getStateStyleables();
    final StyleableDefinition[] result = new StyleableDefinition[styleables.length];

    for (int i = 0; i < styleables.length; i++) {
      result[i] = new MyStyleableDefinition(styleables[i]);
    }
    return result;
  }

  private class MyStyleableDefinition implements StyleableDefinition {
    private final StyleableDefinition myWrappee;

    private MyStyleableDefinition(@NotNull StyleableDefinition wrappee) {
      myWrappee = wrappee;
    }

    @NotNull
    @Override
    public List<StyleableDefinition> getChildren() {
      final List<StyleableDefinition> styleables = myWrappee.getChildren();
      final List<StyleableDefinition> result = new ArrayList<StyleableDefinition>(styleables.size());

      for (StyleableDefinition styleable : styleables) {
        result.add(new MyStyleableDefinition(styleable));
      }
      return result;
    }

    @NotNull
    @Override
    public String getName() {
      return myWrappee.getName();
    }

    @NotNull
    @Override
    public List<AttributeDefinition> getAttributes() {
      final List<AttributeDefinition> result = new ArrayList<AttributeDefinition>();

      for (AttributeDefinition definition : myWrappee.getAttributes()) {
        if (isAttributeAcceptable(definition.getName())) {
          result.add(definition);
        }
      }
      return result;
    }
  }
}
