/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.execution;

import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Assertion;

import java.util.Collections;

/**
 * @author dyoma
 */
public class ParametersListTest extends UsefulTestCase {
  private final Assertion CHECK = new Assertion();

  public void testAddParametersString() {
    checkTokenizer("a b c", new String[]{"a", "b", "c"});
    checkTokenizer("a \"b\"", new String[]{"a", "b"});
    checkTokenizer("a \"b\\\"", new String[]{"a", "b\\\""});
    checkTokenizer("a \"\"", new String[]{"a", "\"\""}); // Bug #12169
    checkTokenizer("a \"x\"", new String[]{"a", "x"});
    checkTokenizer("a \"\" b", new String[]{"a", "\"\"", "b"});
  }

  private void checkTokenizer(String parmsString, String[] expected) {
    ParametersList params = new ParametersList();
    params.addParametersString(parmsString);
    String[] strings = ArrayUtil.toStringArray(params.getList());
    CHECK.compareAll(expected, strings);
  }

  public void testParamsGroup_Empty() {
    ParametersList params = new ParametersList();

    assertEquals(0, params.getParamsGroupsCount());
    assertTrue(params.getParamsGroups().isEmpty());
  }

  public void testParamsGroup_Add() {
    ParametersList params = new ParametersList();

    final ParamsGroup group1 = params.addParamsGroup("id1");
    assertEquals("id1", group1.getId());
    assertEquals(1, params.getParamsGroupsCount());
    assertSameElements(Collections.singletonList(group1), params.getParamsGroups());

    final ParamsGroup group2 = params.addParamsGroup("id2");
    assertEquals("id2", group2.getId());
    assertEquals(2, params.getParamsGroupsCount());
    assertOrderedEquals(params.getParamsGroups(), group1, group2);
  }

  public void testParamsGroup_AddAt() {
    ParametersList params = new ParametersList();

    final ParamsGroup group1 = params.addParamsGroup("id1");
    final ParamsGroup group2 = params.addParamsGroup("id2");

    final ParamsGroup group12 = params.addParamsGroupAt(1, "id12");
    final ParamsGroup group01 = params.addParamsGroupAt(0, "id01");

    assertOrderedEquals(params.getParamsGroups(), group01, group1, group12, group2);
  }

  public void testParamsGroup_Remove() {
    ParametersList params = new ParametersList();

    final ParamsGroup group1 = params.addParamsGroup("id1");
    final ParamsGroup group2 = params.addParamsGroup("id2");
    final ParamsGroup group3 = params.addParamsGroup("id3");
    final ParamsGroup group4 = params.addParamsGroup("id4");

    params.removeParamsGroup(0);
    assertOrderedEquals(params.getParamsGroups(), group2, group3, group4);

    params.removeParamsGroup(1);
    assertOrderedEquals(params.getParamsGroups(), group2, group4);
  }

  public void testParamsGroup_GroupParams() {
    ParametersList params = new ParametersList();
    params.add("param1");

    final ParamsGroup group1 = params.addParamsGroup("id1");
    group1.addParameter("group1_param1");

    params.add("param2");
    group1.addParameter("group1_param2");


    final ParamsGroup group2 = params.addParamsGroup("id2");
    group2.addParameter("group2_param1");

    params.add("param3");

    assertOrderedEquals(params.getParameters(), "param1", "param2", "param3");
    assertOrderedEquals(params.getList(), "param1", "param2", "param3", "group1_param1", "group1_param2", "group2_param1");
    assertOrderedEquals(params.getArray(), "param1", "param2", "param3", "group1_param1", "group1_param2", "group2_param1");
    assertEquals("param1 param2 param3 group1_param1 group1_param2 group2_param1", params.getParametersString().trim());

    final ParametersList group1_params = group1.getParametersList();
    assertOrderedEquals(group1_params.getParameters(), "group1_param1", "group1_param2");
    assertOrderedEquals(group1_params.getList(), "group1_param1", "group1_param2");
    assertOrderedEquals(group1_params.getArray(), "group1_param1", "group1_param2");
    assertEquals("group1_param1 group1_param2", group1_params.getParametersString().trim());

    final ParametersList group2_params = group2.getParametersList();
    assertOrderedEquals(group2_params.getParameters(), "group2_param1");
    assertOrderedEquals(group2_params.getList(), "group2_param1");
    assertOrderedEquals(group2_params.getArray(), "group2_param1");
    assertEquals("group2_param1", group2_params.getParametersString().trim());
  }

  public void testParamsGroup_SubGroups() {
    ParametersList params = new ParametersList();

    final ParamsGroup group1 = params.addParamsGroup("id1");
    group1.addParameter("group1_param1");
    group1.addParameter("group1_param2");

    final ParamsGroup group2 = params.addParamsGroup("id2");
    group2.addParameter("group2_param1");

    final ParamsGroup group1_1 = group1.getParametersList().addParamsGroup("id1_1");
    group1_1.addParameter("group1_1_param1");

    final ParamsGroup group1_2 = group1.getParametersList().addParamsGroup("id1_2");
    group1_2.addParameter("group1_2_param1");

    assertOrderedEquals(params.getList(), "group1_param1", "group1_param2", "group1_1_param1", "group1_2_param1", "group2_param1");
    assertOrderedEquals(params.getList(), "group1_param1", "group1_param2", "group1_1_param1", "group1_2_param1", "group2_param1");
    assertEquals("group1_param1 group1_param2 group1_1_param1 group1_2_param1 group2_param1", params.getParametersString().trim());
  }

  public void testParamsGroup_Clone() {
    ParametersList params = new ParametersList();

    final ParamsGroup group1 = params.addParamsGroup("id1");
    group1.addParameter("group1_param1");
    final ParamsGroup group2 = params.addParamsGroup("id2");
    group2.addParameter("group2_param1");
    final ParamsGroup group3 = params.addParamsGroup("id3");
    group3.addParameter("group3_param1");

    final ParametersList params_clone = params.clone();

    // let's change original params group
    params.removeParamsGroup(0);
    group2.addParameter("group2_param2");

    assertEquals("group2_param1 group2_param2 group3_param1", params.getParametersString().trim());
    assertEquals("group1_param1 group2_param1 group3_param1", params_clone.getParametersString().trim());
  }
}
