/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
