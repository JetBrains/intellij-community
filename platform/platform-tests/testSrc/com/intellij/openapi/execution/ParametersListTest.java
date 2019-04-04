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
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.execution.ParametersListUtil;
import org.junit.After;
import org.junit.Before;
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
    checkTokenizer("", ArrayUtil.EMPTY_STRING_ARRAY);
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
    String[] parameters = {"simpleParam", "param with spaces", "withQuote=\"", "param=\"complex quoted\"", "C:\\\"q\"", "C:\\w s\\"};
    ParametersList parametersList = new ParametersList();
    parametersList.addAll(parameters);
    String joined = parametersList.getParametersString();
    assertEquals("simpleParam \"param with spaces\" withQuote=\\\" \"param=\\\"complex quoted\\\"\" C:\\\\\"q\\\" \"C:\\w s\"\\", joined);
    checkTokenizer(joined, parameters);
  }

  @Test
  public void properties() {
    ParametersList params = new ParametersList();
    params.addProperty("foo.foo", "\"bar bar\" bar");
    assertEquals(1, params.getProperties().size());
    assertEquals("\"bar bar\" bar", params.getProperties().get("foo.foo"));
  }

  @Test
  public void addEmptyProperties() {
    ParametersList params = new ParametersList();
    params.addProperty("foo.null", null);
    params.addProperty("foo.empty", "");
    params.addProperty("foo.value.less");
    params.defineProperty("def.null", null);
    params.defineProperty("def.empty", "");
    params.defineProperty("foo.value.less", "anyway");
    params.addNotEmptyProperty("empty.null", null);
    params.addNotEmptyProperty("empty.empty", "");
    params.addNotEmptyProperty("empty.spaces", "   \t\t\t");
    assertTrue(params.hasProperty("foo.value.less"));
    assertTrue(params.hasProperty("foo.empty"));
    assertEquals("{foo.empty=, foo.value.less=, def.empty=}", params.getProperties().toString());
  }

  @Test
  public void redefineProperty() {
    ParametersList params = new ParametersList();
    params.defineProperty("sample", "foo");
    params.defineProperty("sample", "bar");
    assertEquals(1, params.getProperties().size());
    assertEquals("foo", params.getPropertyValue("sample"));
    params.addProperty("sample", "baz");
    assertEquals(1, params.getProperties().size());
    assertEquals("baz", params.getPropertyValue("sample"));
    params.defineProperty("ample.sample", "qux");
    assertEquals(2, params.getProperties().size());
    params.defineSystemProperty("ParameterListTest.prop");
    params.defineSystemProperty("ParameterListTest.missing.prop");
    assertNull(params.getPropertyValue("ParameterListTest.prop.value"));
    assertEquals("my.system.value", params.getPropertyValue("ParameterListTest.prop"));
  }

  @Test
  public void reAddProperty() {
    ParametersList params = new ParametersList();
    params.add("-Dp8=5");
    params.add("-DpX=none");
    params.addProperty("simple.prop");
    params.addProperty("simple");
    params.addProperty("foo", "$foo");
    params.addProperty("bar", "$bar");
    params.addProperty("foo", "$foo.foo");
    params.addProperty("foo", "");
    params.addProperty("simple");
    params.addProperty("pX");
    params.replaceOrAppend("-Dp8=", "-Dp8=8");
    params.replaceOrAppend("-Dp27", "-Dp27=21");
    params.replaceOrPrepend("-Dp27=", "-Dp27=27");
    params.replaceOrPrepend("-Dp1st", "-Dp1st");
    params.add("-Dsimple");
    assertEquals("-Dp1st -Dp8=8 -DpX -Dsimple.prop -Dsimple -Dfoo -Dbar=$bar -Dp27=27 -Dsimple", params.getParametersString());
  }

  @Before
  public void initMacros() {
    System.setProperty("ParameterListTest.prop", "my.system.value");
    ParametersList.setTestMacros(JBIterable.of("foo", "bar", "baz", "qux").toReverseMap(o -> "env." + o));
  }

  @Test
  public void macrosInParameters() {
    ParametersList params = new ParametersList();
    params.add("foo", "${env.foo}");
    params.add("${env.bar}", "bar");
    params.add("${env.baz}:${env.baz}");
    params.addParametersString("-${env.baz} \"foo:${env.foo}\"");
    assertEquals("[foo, foo, ${env.bar}, bar, baz:baz, -baz, foo:foo]", params.toString());
    params.addParamsGroup("group").addParameter("${env.qux}");
    params.addParamsGroupAt(0, new ParamsGroup("first")).addParameter("${env.baz}");
    assertEquals("foo foo ${env.bar} bar baz:baz -baz foo:foo baz qux", params.getParametersString());
    assertEquals("[foo, foo, ${env.bar}, bar, baz:baz, -baz, foo:foo] and [first:[baz], group:[qux]]", params.toString());
    params.replaceOrPrepend("foo", "${env.foo}:X");
    assertEquals("foo foo ${env.bar} bar baz:baz -baz foo:X baz qux", params.getParametersString());
  }

  @Test
  public void macroInProperties() {
    ParametersList params = new ParametersList();
    params.defineProperty("foo", "${env.foo}");
    assertEquals("foo", params.getPropertyValue("foo"));
    params.defineProperty("bar", "${env.bar}");
    assertEquals("bar", params.getPropertyValue("bar"));
    params.defineProperty("foo.bar", "${env.foo}${env.bar}");
    assertEquals("foobar", params.getPropertyValue("foo.bar"));
    params.defineProperty("foo.bar.ex", "prefix.${env.foo}.base.${env.bar}.suffix");
    assertEquals("prefix.foo.base.bar.suffix", params.getPropertyValue("foo.bar.ex"));
    params.defineProperty("foo.bar.flux", "pref${ix.${env.foo}.${base}.${env.bar}.suf}fix");
    assertEquals("pref${ix.foo.${base}.bar.suf}fix", params.getPropertyValue("foo.bar.flux"));
  }
  
  @After
  public void clearMacros() {
    ParametersList.setTestMacros(null);
    System.clearProperty("ParameterListTest.prop");
  }

  private static void checkTokenizer(String paramString, String... expected) {
    ParametersList params = new ParametersList();
    params.addParametersString(paramString);
    assertEquals(asList(expected), params.getList());

    List<String> lines = ParametersListUtil.parse(paramString, true);
    assertEquals(paramString, StringUtil.join(lines, " "));
  }

  @Test
  public void testParameterListUtil() {
    final List<String> expected = asList(
      "cmd",
      "-a",
      "-b",
      "arg0",
      "-c",
      "--long-option",
      "--long-opt2=arg1",
      "arg2",
      "arg3",
      "-a",
      "a \"r g",
      "--foo=d e f"
    );
    final String doubleQuotes = "cmd -a -b arg0 -c --long-option    --long-opt2=arg1 arg2 arg3 -a \"a \\\"r g\" --foo=\"d e f\"\"\"";
    assertEquals("Double quotes broken", expected, ParametersListUtil.parse(doubleQuotes, false, true));

    final String singleQuotes = "cmd -a -b arg0 -c --long-option    --long-opt2=arg1 arg2 arg3 -a 'a \"r g' --foo='d e f'";
    assertEquals("Single quotes broken", expected, ParametersListUtil.parse(singleQuotes, false, true));

    final String mixedQuotes = "cmd -a -b arg0 -c --long-option    --long-opt2=arg1 arg2 arg3 -a \"a \\\"r g\" --foo='d e f'";
    assertEquals("Mixed quotes broken", expected, ParametersListUtil.parse(mixedQuotes, false, true));
  }
}
