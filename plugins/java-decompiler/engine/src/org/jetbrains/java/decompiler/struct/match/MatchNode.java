/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler.struct.match;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.java.decompiler.struct.match.IMatchable.MatchProperties;

public class MatchNode {

  public static class RuleValue {
    public final int parameter;
    public final Object value;
    
    public RuleValue(int parameter, Object value) {
      this.parameter = parameter;
      this.value = value;
    }
    
    public boolean isVariable() {
      String strValue = value.toString();
      return (strValue.charAt(0) == '$' && strValue.charAt(strValue.length() - 1) == '$');
    }
    
    public String toString() {
      return value.toString();
    }
  }
  
  public static final int MATCHNODE_STATEMENT = 0;
  public static final int MATCHNODE_EXPRENT = 1;
  
  private final int type;
  
  private final Map<MatchProperties, RuleValue> rules = new HashMap<>();
  
  private final List<MatchNode> children = new ArrayList<>();
  
  
  public MatchNode(int type) {
    this.type = type;
  }

  public void addChild(MatchNode child) {
    children.add(child);
  }
  
  public void addRule(MatchProperties property, RuleValue value) {
    rules.put(property, value);
  }

  public int getType() {
    return type;
  }

  public List<MatchNode> getChildren() {
    return children;
  }

  public Map<MatchProperties, RuleValue> getRules() {
    return rules;
  }
  
  public Object getRuleValue(MatchProperties property) {
    RuleValue rule = rules.get(property);
    return rule == null ? null : rule.value;
  }
  
}
