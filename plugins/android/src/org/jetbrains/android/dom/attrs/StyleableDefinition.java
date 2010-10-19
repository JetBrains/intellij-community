package org.jetbrains.android.dom.attrs;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole, coyote
 */
public class StyleableDefinition {
  private final String myName;
  private final List<StyleableDefinition> parents = new ArrayList<StyleableDefinition>();
  private final List<AttributeDefinition> myAttributes = new ArrayList<AttributeDefinition>();
  private final List<StyleableDefinition> children = new ArrayList<StyleableDefinition>();

  public StyleableDefinition(@NotNull String name) {
    myName = name;
  }

  public void addChild(@NotNull StyleableDefinition child) {
    children.add(child);
  }

  public void addParent(@NotNull StyleableDefinition parent) {
    parents.add(parent);
  }

  @NotNull
  public List<StyleableDefinition> getParents() {
    return parents;
  }

  @NotNull
  public List<StyleableDefinition> getChildren() {
    return children;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public void addAttribute(@NotNull AttributeDefinition attrDef) {
    myAttributes.add(attrDef);
  }

  @NotNull
  public List<AttributeDefinition> getAttributes() {
    return Collections.unmodifiableList(myAttributes);
  }
}
