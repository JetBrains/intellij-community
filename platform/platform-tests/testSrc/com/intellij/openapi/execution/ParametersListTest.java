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
package com.intellij.openapi.execution;

import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.execution.ParametersListUtil;
import org.junit.Test;

import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;

public class ParametersListTest {
  @Test
  public void paramsGroupEmpty() {
    ParametersList params = new ParametersList();
    assertEquals(0, params.getParamsGroupsCount());
    assertTrue(params.getParamsGroups().isEmpty());
  }

  @Test
  public void paramsGroupAdd() {
    ParametersList params = new ParametersList();

    ParamsGroup group1 = params.addParamsGroup("id1");
    assertEquals("id1", group1.getId());
    assertEquals(1, params.getParamsGroupsCount());
    assertEquals(asList(group1), params.getParamsGroups());

    ParamsGroup group2 = params.addParamsGroup("id2");
    assertEquals("id2", group2.getId());
    assertEquals(2, params.getParamsGroupsCount());
    assertEquals(asList(group1, group2), params.getParamsGroups());
  }

  @Test
  public void paramsGroupAddAt() {
    ParametersList params = new ParametersList();
    ParamsGroup group1 = params.addParamsGroup("id1");
    ParamsGroup group2 = params.addParamsGroup("id2");
    ParamsGroup group12 = params.addParamsGroupAt(1, "id12");
    ParamsGroup group01 = params.addParamsGroupAt(0, "id01");
    assertEquals(asList(group01, group1, group12, group2), params.getParamsGroups());
  }

  @Test
  public void paramsGroupRemove() {
    ParametersList params = new ParametersList();
    params.addParamsGroup("id1");
    ParamsGroup group2 = params.addParamsGroup("id2");
    ParamsGroup group3 = params.addParamsGroup("id3");
    ParamsGroup group4 = params.addParamsGroup("id4");
    params.removeParamsGroup(0);
    assertEquals(asList(group2, group3, group4), params.getParamsGroups());

    params.removeParamsGroup(1);
    assertEquals(asList(group2, group4), params.getParamsGroups());
  }

  @Test
  public void paramsGroupGroupParams() {
    ParametersList params = new ParametersList();
    params.add("param1");
    ParamsGroup group1 = params.addParamsGroup("id1");
    group1.addParameter("group1_param1");
    params.add("param2");
    group1.addParameter("group1_param2");
    ParamsGroup group2 = params.addParamsGroup("id2");
    group2.addParameter("group2_param1");
    params.add("param3");
    assertEquals(asList("param1", "param2", "param3"), params.getParameters());
    assertEquals(asList("param1", "param2", "param3", "group1_param1", "group1_param2", "group2_param1"), params.getList());
    assertArrayEquals(new String[]{"param1", "param2", "param3", "group1_param1", "group1_param2", "group2_param1"}, params.getArray());
    assertEquals("param1 param2 param3 group1_param1 group1_param2 group2_param1", params.getParametersString().trim());

    ParametersList group1_params = group1.getParametersList();
    assertEquals(asList("group1_param1", "group1_param2"), group1_params.getParameters());
    assertEquals(asList("group1_param1", "group1_param2"), group1_params.getList());
    assertArrayEquals(new String[]{"group1_param1", "group1_param2"}, group1_params.getArray());
    assertEquals("group1_param1 group1_param2", group1_params.getParametersString().trim());

    ParametersList group2_params = group2.getParametersList();
    assertEquals(asList("group2_param1"), group2_params.getParameters());
    assertEquals(asList("group2_param1"), group2_params.getList());
    assertArrayEquals(new String[]{"group2_param1"}, group2_params.getArray());
    assertEquals("group2_param1", group2_params.getParametersString().trim());
  }

  @Test
  public void paramsGroupSubGroups() {
    ParametersList params = new ParametersList();
    ParamsGroup group1 = params.addParamsGroup("id1");
    group1.addParameter("group1_param1");
    group1.addParameter("group1_param2");
    ParamsGroup group2 = params.addParamsGroup("id2");
    group2.addParameter("group2_param1");
    ParamsGroup group1_1 = group1.getParametersList().addParamsGroup("id1_1");
    group1_1.addParameter("group1_1_param1");
    ParamsGroup group1_2 = group1.getParametersList().addParamsGroup("id1_2");
    group1_2.addParameter("group1_2_param1");
    assertEquals(asList("group1_param1", "group1_param2", "group1_1_param1", "group1_2_param1", "group2_param1"), params.getList());
    assertEquals(asList("group1_param1", "group1_param2", "group1_1_param1", "group1_2_param1", "group2_param1"), params.getList());
    assertEquals("group1_param1 group1_param2 group1_1_param1 group1_2_param1 group2_param1", params.getParametersString().trim());
  }

  @Test
  public void paramsGroupClone() {
    ParametersList params = new ParametersList();
    ParamsGroup group1 = params.addParamsGroup("id1");
    group1.addParameter("group1_param1");
    ParamsGroup group2 = params.addParamsGroup("id2");
    group2.addParameter("group2_param1");
    ParamsGroup group3 = params.addParamsGroup("id3");
    group3.addParameter("group3_param1");
    ParametersList params_clone = params.clone();
    params.removeParamsGroup(0);
    group2.addParameter("group2_param2");
    assertEquals("group2_param1 group2_param2 group3_param1", params.getParametersString().trim());
    assertEquals("group1_param1 group2_param1 group3_param1", params_clone.getParametersString().trim());
  }

  @Test
  public void addParametersString() {
    checkTokenizer("a b c",
                   "a", "b", "c");
    checkTokenizer("a \"b\"",
                   "a", "b");
    checkTokenizer("a \"b\\\"",
                   "a", "b\"");
    checkTokenizer("a \"\"",
                   "a", ""); // Bug #12169
    checkTokenizer("a \"x\"",
                   "a", "x");
    checkTokenizer("a \"\\\"\" b",
                   "a", "\"", "b");
  }

  @Test
  public void paramsWithSpacesAndQuotes() {
    checkTokenizer("a b=\"some text\" c",
                   "a", "b=some text", "c");
    checkTokenizer("a b=\"some text with spaces\" c",
                   "a", "b=some text with spaces", "c");
    checkTokenizer("a b=\"some text with spaces\".more c",
                   "a", "b=some text with spaces.more", "c");
    checkTokenizer("a b=\"some text with spaces \"more c",
                   "a", "b=some text with spaces more", "c");
    checkTokenizer("a \"some text with spaces\"More c",
                   "a", "some text with spacesMore", "c");
    checkTokenizer("a \"some text with spaces more c",
                   "a", "some text with spaces more c");
    checkTokenizer("a\"Some text with spaces \"more c",
                   "aSome text with spaces more", "c");
    checkTokenizer("a\"Some text with spaces \"more",
                   "aSome text with spaces more");
    checkTokenizer("a\"Some text with spaces \"more next\"Text moreText\"End c",
                   "aSome text with spaces more", "nextText moreTextEnd", "c");
    checkTokenizer("\"\"C:\\phing.bat\"",
                   "C:\\phing.bat");
    checkTokenizer("-Dp.1=\"some text\" -Dp.2=\\\"value\\\"",
                   "-Dp.1=some text", "-Dp.2=\"value\"");
    checkTokenizer("-Dp.1=- -dump-config",
                   "-Dp.1=-", "-dump-config");
  }

  @Test
  public void joiningParams() {
    String[] parameters = {"simpleParam", "param with spaces", "withQuote=\"", "param=\"complex quoted\""};
    ParametersList parametersList = new ParametersList();
    parametersList.addAll(parameters);
    String joined = parametersList.getParametersString();
    assertEquals("simpleParam \"param with spaces\" withQuote=\\\" \"param=\\\"complex quoted\\\"\"", joined);
    checkTokenizer(joined, parameters);
  }

  @Test
  public void properties() {
    ParametersList params = new ParametersList();
    params.addProperty("foo.foo", "\"bar bar\" bar");
    assertEquals(1, params.getProperties().size());
    assertEquals("\"bar bar\" bar", params.getProperties().get("foo.foo"));
  }

  private static void checkTokenizer(String paramString, String... expected) {
    ParametersList params = new ParametersList();
    params.addParametersString(paramString);
    assertEquals(asList(expected), params.getList());

    List<String> lines = ParametersListUtil.parse(paramString, true);
    assertEquals(paramString, StringUtil.join(lines, " "));
  }
}
