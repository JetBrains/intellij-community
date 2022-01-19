// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A list of command-line parameters featuring the following:
 * <ul>
 *   <li>special handling for Java properties ({@code -D<name>=<value>})</li>
 *   <li>macro substitution upon addition for plain parameters, and property values</li>
 *   <li>named groups for parameters</li>
 *   <li>parameter strings with quoted parameters</li>
 * </ul>
 *
 * @see ParametersList#defineProperty(String, String)
 * @see ParametersList#expandMacros(String)
 * @see ParametersList#addParamsGroup(String)
 * @see ParametersList#addParametersString(String)
 * @see ParamsGroup
 */
public final class ParametersList implements Cloneable {
  private static final Pattern PROPERTY_PATTERN = Pattern.compile("-D(\\S+?)(?:=(.+))?");
  private static final Pattern MACRO_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

  private final List<CompositeParameterTargetedValue> myParameters = new ArrayList<>();
  private final List<ParamsGroup> myGroups = new SmartList<>();
  private final NotNullLazyValue<Map<String, String>> myMacroMap = NotNullLazyValue.lazy(ParametersList::computeMacroMap);

  public boolean hasParameter(@NotNull @NonNls String parameter) {
    return ContainerUtil.lastIndexOf(myParameters, value -> parameter.equals(value.getLocalValue())) != -1;
  }

  public boolean hasProperty(@NotNull @NonNls String propertyName) {
    return getPropertyValue(propertyName) != null;
  }

  @Nullable
  public String getPropertyValue(@NotNull @NonNls String propertyName) {
    String exact = "-D" + propertyName;
    String prefix = "-D" + propertyName + "=";
    int index = indexOfLocalParameter(o -> o.equals(exact) || o.startsWith(prefix));
    if (index < 0) return null;
    String str = myParameters.get(index).getLocalValue();
    return str.length() == exact.length() ? "" : str.substring(prefix.length());
  }

  @NotNull
  public Map<String, String> getProperties() {
    return getProperties("");
  }

  @NotNull
  public Map<String, String> getProperties(@NonNls String valueIfMissing) {
    Map<String, String> result = new LinkedHashMap<>();
    JBIterable<Matcher> matchers =
      JBIterable.from(myParameters).map(CompositeParameterTargetedValue::getLocalValue).map(PROPERTY_PATTERN::matcher).filter(Matcher::matches);
    for (Matcher matcher : matchers) {
      result.put(matcher.group(1), StringUtil.notNullize(matcher.group(2), valueIfMissing));
    }
    return result;
  }

  @NotNull
  public String getParametersString() {
    return join(getList());
  }

  public String @NotNull [] getArray() {
    return ArrayUtilRt.toStringArray(getList());
  }

  @NotNull
  public List<String> getList() {
    if (myGroups.isEmpty()) {
      return Collections.unmodifiableList(getLocalParameters());
    }

    List<String> params = new ArrayList<>(getLocalParameters());
    for (ParamsGroup group : myGroups) {
      params.addAll(group.getParameters());
    }
    return Collections.unmodifiableList(params);
  }

  @NotNull
  public List<CompositeParameterTargetedValue> getTargetedList() {
    if (myGroups.isEmpty()) {
      return Collections.unmodifiableList(myParameters);
    }

    List<CompositeParameterTargetedValue> params = new ArrayList<>(myParameters);
    for (ParamsGroup group : myGroups) {
      params.addAll(CompositeParameterTargetedValue.targetizeParameters(group.getParameters()));
    }
    return Collections.unmodifiableList(params);
  }

  @NotNull
  private List<String> getLocalParameters() {
    return ContainerUtil.map(myParameters, CompositeParameterTargetedValue::getLocalValue);
  }

  @NotNull
  private CompositeParameterTargetedValue createExpandedLocalValue(String param) {
    return new CompositeParameterTargetedValue(expandMacros(param));
  }

  public void clearAll() {
    myParameters.clear();
    myGroups.clear();
  }

  public void prepend(@NotNull @NonNls String parameter) {
    addAt(0, parameter);
  }

  public void prepend(@Nullable CompositeParameterTargetedValue parameterTargetedValue) {
    myParameters.add(0, parameterTargetedValue);
  }

  public void prependAll(@NonNls String @NotNull ... parameter) {
    addAll(parameter);
    Collections.rotate(myParameters, parameter.length);
  }

  public void addParametersString(@Nullable @NonNls String parameters) {
    if (StringUtil.isEmptyOrSpaces(parameters)) return;
    for (String param : parse(parameters)) {
      add(param);
    }
  }

  public void add(@Nullable @NonNls String parameter) {
    if (parameter == null) return;
    myParameters.add(createExpandedLocalValue(parameter));
  }

  public void add(@Nullable CompositeParameterTargetedValue parameterTargetedValue){
    if (parameterTargetedValue == null) return;
    myParameters.add(parameterTargetedValue);
  }

  @NotNull
  public ParamsGroup addParamsGroup(@NotNull @NonNls String groupId) {
    return addParamsGroup(new ParamsGroup(groupId));
  }

  @NotNull
  public ParamsGroup addParamsGroup(@NotNull ParamsGroup group) {
    myGroups.add(group);
    return group;
  }

  @NotNull
  public ParamsGroup addParamsGroupAt(int index, @NotNull ParamsGroup group) {
    myGroups.add(index, group);
    return group;
  }

  @NotNull
  public ParamsGroup addParamsGroupAt(int index, @NotNull @NonNls String groupId) {
    ParamsGroup group = new ParamsGroup(groupId);
    myGroups.add(index, group);
    return group;
  }

  public int getParametersCount() {
    return myParameters.size();
  }

  public int getParamsGroupsCount() {
    return myGroups.size();
  }

  @NotNull
  public List<String> getParameters() {
    return Collections.unmodifiableList(getLocalParameters());
  }

  @NotNull
  public List<ParamsGroup> getParamsGroups() {
    return Collections.unmodifiableList(myGroups);
  }

  @NotNull
  public ParamsGroup getParamsGroupAt(int index) {
    return myGroups.get(index);
  }

  @Nullable
  public ParamsGroup getParamsGroup(@NotNull @NonNls String name) {
    for (ParamsGroup group : myGroups) {
      if (name.equals(group.getId())) return group;
    }
    return null;
  }

  @Nullable
  public ParamsGroup removeParamsGroup(int index) {
    return myGroups.remove(index);
  }

  public void addAt(int index, @NotNull @NonNls String parameter) {
    myParameters.add(index, createExpandedLocalValue(parameter));
  }

  /**
   * Keeps the {@code <propertyName>} property if defined; or defines it with {@code System.getProperty()} as a value if present.
   */
  public void defineSystemProperty(@NotNull @NonNls String propertyName) {
    defineProperty(propertyName, System.getProperty(propertyName));
  }

  /**
   * Keeps the {@code <propertyName>} property if defined; otherwise appends the new one ignoring null values.
   */
  public void defineProperty(@NotNull @NonNls String propertyName, @Nullable @NonNls String propertyValue) {
    if (propertyValue == null) return;
    @NlsSafe String exact = "-D" + propertyName;
    @NlsSafe String prefix = "-D" + propertyName + "=";
    int index = indexOfLocalParameter(o -> o.equals(exact) || o.startsWith(prefix));
    if (index > -1) return;
    String value = propertyValue.isEmpty() ? exact : prefix + expandMacros(propertyValue);
    myParameters.add(new CompositeParameterTargetedValue(value));
  }

  /**
   * Adds {@code -D<propertyName>} to the list; replaces the value of the last property if defined.
   */
  public void addProperty(@NotNull @NonNls String propertyName) {
    @NlsSafe String exact = "-D" + propertyName;
    @NlsSafe String prefix = "-D" + propertyName + "=";
    replaceOrAddAt(new CompositeParameterTargetedValue(exact), myParameters.size(), o -> o.equals(exact) || o.startsWith(prefix));
  }

  /**
   * Adds {@code -D<propertyName>=<propertyValue>} to the list ignoring null values;
   * replaces the value of the last property if defined.
   */
  public void addProperty(@NotNull @NonNls String propertyName, @Nullable @NonNls String propertyValue) {
    if (propertyValue == null) return;
    @NlsSafe String exact = "-D" + propertyName;
    @NlsSafe String prefix = "-D" + propertyName + "=";
    String value = propertyValue.isEmpty() ? exact : prefix + expandMacros(propertyValue);
    replaceOrAddAt(new CompositeParameterTargetedValue(value), myParameters.size(), o -> o.equals(exact) || o.startsWith(prefix));
  }

  /**
   * Adds {@code -D<propertyName>=<propertyValue>} to the list ignoring null, empty and spaces-only values;
   * replaces the value of the last property if defined.
   */
  public void addNotEmptyProperty(@NotNull @NonNls String propertyName, @Nullable @NonNls String propertyValue) {
    if (StringUtil.isEmptyOrSpaces(propertyValue)) return;
    addProperty(propertyName, propertyValue);
  }

  /**
   * Replaces the last parameter that starts with the {@code <parameterPrefix>} with {@code <replacement>};
   * otherwise appends {@code <replacement>} to the list.
   */
  public void replaceOrAppend(@NotNull @NonNls String parameterPrefix, @NotNull @NonNls String replacement) {
    replaceOrAddAt(createExpandedLocalValue(replacement), myParameters.size(), o -> o.startsWith(parameterPrefix));
  }

  /**
   * Replaces the last parameter that starts with the {@code <parameterPrefix>} with {@code <replacement>};
   * otherwise prepends this list with {@code <replacement>}.
   */
  public void replaceOrPrepend(@NotNull @NonNls String parameterPrefix, @NotNull @NonNls String replacement) {
    replaceOrAddAt(createExpandedLocalValue(replacement), 0, o -> o.startsWith(parameterPrefix));
  }

  private void replaceOrAddAt(@NotNull CompositeParameterTargetedValue replacement,
                              int position,
                              @NotNull Condition<? super String> existingCondition) {
    int index = indexOfLocalParameter(existingCondition);
    boolean setNewValue = StringUtil.isNotEmpty(replacement.getLocalValue());
    if (index > -1 && setNewValue) {
      myParameters.set(index, replacement);
    }
    else if (index > -1) {
      myParameters.remove(index);
    }
    else if (setNewValue) {
      myParameters.add(position, replacement);
    }
  }

  private int indexOfLocalParameter(@NotNull @NonNls Condition<? super String> condition) {
    return ContainerUtil.lastIndexOf(myParameters, value -> condition.value(value.getLocalValue()));
  }

  public void set(int ind, @NotNull @NonNls String value) {
    myParameters.set(ind, new CompositeParameterTargetedValue(value));
  }

  public String get(int ind) {
    return myParameters.get(ind).getLocalValue();
  }

  @Nullable
  public String getLast() {
    return myParameters.size() > 0 ? myParameters.get(myParameters.size() - 1).getLocalValue() : null;
  }

  public void add(@NotNull @NonNls String name, @NotNull @NonNls String value) {
    myParameters.add(new CompositeParameterTargetedValue(name)); // do not expand macros in parameter name
    add(value);
  }

  public void addAll(@NonNls String @NotNull ... parameters) {
    addAll(Arrays.asList(parameters));
  }

  public void addAll(@NotNull @NonNls List<String> parameters) {
    // Don't use myParameters.addAll(parameters) , it does not call expandMacros(parameter)
    for (String parameter : parameters) {
      add(parameter);
    }
  }

  /** @noinspection MethodDoesntCallSuperMethod*/
  @Override
  public ParametersList clone() {
    return copyTo(new ParametersList());
  }

  @NotNull
  public ParametersList copyTo(@NotNull ParametersList target) {
    target.myParameters.addAll(myParameters);
    for (ParamsGroup group : myGroups) {
      target.myGroups.add(group.clone());
    }
    return target;
  }

  /**
   * @see ParametersListUtil#join(List)
   */
  @NotNull
  public static String join(@NotNull @NonNls List<String> parameters) {
    return ParametersListUtil.join(parameters);
  }

  /**
   * @see ParametersListUtil#join(List)
   */
  @NotNull
  public static String join(@NonNls String @NotNull ... parameters) {
    return ParametersListUtil.join(parameters);
  }

  /**
   * @see ParametersListUtil#parseToArray(String)
   */
  public static String @NotNull [] parse(@NotNull @NonNls String string) {
    return ParametersListUtil.parseToArray(string);
  }

  @NotNull
  public String expandMacros(@NotNull @NonNls String text) {
    int start = text.indexOf("${");
    if (start < 0) return text;
    Map<String, String> macroMap = myMacroMap.getValue();
    Matcher matcher = MACRO_PATTERN.matcher(text);
    StringBuilder sb = null;
    while (matcher.find(start)) {
      String value = macroMap.get(matcher.group(1));
      if (value != null) {
        if (sb == null) sb = new StringBuilder(2 * text.length()).append(text, 0, matcher.start());
        else sb.append(text, start, matcher.start());
        sb.append(value);
        start = matcher.end();
      }
      else {
        if (sb != null) sb.append(text, start, matcher.start() + 2);
        start = matcher.start() + 2;
      }
    }
    return sb == null ? text : sb.append(text, start, text.length()).toString();
  }

  public void patchMacroWithEnvs(Map<String, String> envs) {
    myMacroMap.getValue().putAll(envs);
  }

  private static Map<String, String> ourTestMacros;

  @TestOnly
  public static void setTestMacros(@Nullable @NonNls Map<String, String> testMacros) {
    ourTestMacros = testMacros;
  }

  private static @NotNull Map<String, String> computeMacroMap() {
    // ApplicationManager.getApplication() will return null if executed in ParameterListTest
    Application application = ApplicationManager.getApplication();
    if (application == null || application.isUnitTestMode() && ourTestMacros != null) {
      return ObjectUtils.notNull(ourTestMacros, Collections.emptyMap());
    }

    Map<String, String> map = CollectionFactory.createCaseInsensitiveStringMap();
    Map<String, String> pathMacros = PathMacros.getInstance().getUserMacros();
    if (!pathMacros.isEmpty()) {
      for (String name : pathMacros.keySet()) {
        String value = pathMacros.get(name);
        if (value != null) {
          map.put(name, value);
        }
      }
    }
    for (Map.Entry<String, String> entry : EnvironmentUtil.getEnvironmentMap().entrySet()) {
      map.putIfAbsent(entry.getKey(), entry.getValue());
    }
    return map;
  }

  @NonNls
  @Override
  public String toString() {
    return myParameters + (myGroups.isEmpty() ? "" : " and " + myGroups);
  }
}