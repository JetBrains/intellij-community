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
 * Represents chunk of values which can be added to a {@link com.intellij.xdebugger.frame.XCompositeNode composite node}
 * @see com.intellij.xdebugger.frame.XCompositeNode#addChildren(XValueChildrenList, boolean)
 *
 * @author nik
 */
public class XValueChildrenList {
  public static final XValueChildrenList EMPTY = new XValueChildrenList(Collections.<String>emptyList(), Collections.<XValue>emptyList());
  private final List<String> myNames;
  private final List<XValue> myValues;
  private final List<XValueGroup> myTopGroups;
  private final List<XValueGroup> myBottomGroups = new SmartList<XValueGroup>();

  public XValueChildrenList(int initialCapacity) {
    this(new ArrayList<String>(initialCapacity), new ArrayList<XValue>(initialCapacity), new SmartList<XValueGroup>());
  }

  public XValueChildrenList() {
    this(new SmartList<String>(), new SmartList<XValue>(), new SmartList<XValueGroup>());
  }

  private XValueChildrenList(@NotNull List<String> names, @NotNull List<XValue> values, @NotNull List<XValueGroup> topGroups) {
    myNames = names;
    myValues = values;
    myTopGroups = topGroups;
  }

  private XValueChildrenList(List<String> names, List<XValue> values) {
    this(names, values, new SmartList<XValueGroup>());
  }

  public static XValueChildrenList singleton(String name, @NotNull XValue value) {
    return new XValueChildrenList(Collections.singletonList(name), Collections.singletonList(value));
  }

  public static XValueChildrenList singleton(@NotNull XNamedValue value) {
    return new XValueChildrenList(Collections.singletonList(value.getName()), Collections.<XValue>singletonList(value));
  }

  public static XValueChildrenList bottomGroup(@NotNull XValueGroup group) {
    XValueChildrenList list = new XValueChildrenList();
    list.addBottomGroup(group);
    return list;
  }

  public static XValueChildrenList topGroups(@NotNull List<XValueGroup> topGroups) {
    return new XValueChildrenList(Collections.<String>emptyList(), Collections.<XValue>emptyList(), topGroups);
  }

  public void add(@NonNls String name, @NotNull XValue value) {
    myNames.add(name);
    myValues.add(value);
  }

  public void add(@NotNull XNamedValue value) {
    myNames.add(value.getName());
    myValues.add(value);
  }

  /**
   * Adds a node representing group of values to the top of a node children list
   */
  public void addTopGroup(@NotNull XValueGroup group) {
    myTopGroups.add(group);
  }

  /**
   * Adds a node representing group of values to the bottom of a node children list
   */
  public void addBottomGroup(@NotNull XValueGroup group) {
    myBottomGroups.add(group);
  }

  public int size() {
    return myNames.size();
  }

  public String getName(int i) {
    return myNames.get(i);
  }

  public XValue getValue(int i) {
    return myValues.get(i);
  }

  public List<XValueGroup> getTopGroups() {
    return myTopGroups;
  }

  public List<XValueGroup> getBottomGroups() {
    return myBottomGroups;
  }
}