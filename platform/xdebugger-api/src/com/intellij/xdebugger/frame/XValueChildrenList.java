/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.xdebugger.frame;

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class XValueChildrenList extends XValueChildrenProvider {
  public static final XValueChildrenList EMPTY = new XValueChildrenList(Collections.<String>emptyList(), Collections.<XValue>emptyList());

  private final List<String> myNames;
  private final List<XValue> myValues;

  public XValueChildrenList(int initialCapacity) {
    myNames = new ArrayList<String>(initialCapacity);
    myValues = new ArrayList<XValue>(initialCapacity);
  }

  public XValueChildrenList() {
    myNames = new SmartList<String>();
    myValues = new SmartList<XValue>();
  }

  public static XValueChildrenList singleton(String name, @NotNull XValue value) {
    return new XValueChildrenList(Collections.singletonList(name), Collections.singletonList(value));
  }

  private XValueChildrenList(List<String> names, List<XValue> values) {
    myNames = names;
    myValues = values;
  }

  public void add(@NonNls String name, @NotNull XValue value) {
    myNames.add(name);
    myValues.add(value);
  }

  @Override
  public int size() {
    return myNames.size();
  }

  @Override
  public String getName(int i) {
    return myNames.get(i);
  }

  @Override
  public XValue getValue(int i) {
    return myValues.get(i);
  }
}