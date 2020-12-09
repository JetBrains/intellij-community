/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.parser.elements;

import com.google.common.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a method call expression element.
 */
public final class GradleDslMethodCall extends GradleDslSimpleExpression {
  public static final Logger LOG = Logger.getInstance(GradleDslMethodCall.class);
  /**
   * The name of the method that this method call is invoking.
   * For example:
   * storeFile file('file.txt') -> myMethodName = file
   * google()                   -> myMethodName = google
   * System.out.println('text') -> myMethodName = System.out.println
   */
  @NotNull private String myMethodName;
  @NotNull private GradleDslExpressionList myArguments;
  private boolean myIsConstructor;

  /**
   * Create a new method call.
   *
   * @param parent     the parent element.
   * @param name       element name, this should be null if the method call is on its own, Ex: "jcenter()"
   * @param methodName the method name of this method call,  Ex: to create "compile project(':xyz')",
   *                   use "project" as statement name and "compile" as element name.
   */
  public GradleDslMethodCall(@NotNull GradleDslElement parent, @NotNull GradleNameElement name, @NotNull String methodName) {
    super(parent, null, name, null);
    myMethodName = methodName;
    myArguments = new GradleDslExpressionList(this, GradleNameElement.empty(), false);
    myIsConstructor = false;
  }

  public GradleDslMethodCall(@NotNull GradleDslElement parent,
                             @NotNull PsiElement methodCall,
                             @NotNull GradleNameElement name,
                             @NotNull String methodName,
                             boolean isConstructor) {
    super(parent, methodCall, name, methodCall);
    myMethodName = methodName;
    myArguments = new GradleDslExpressionList(this, GradleNameElement.empty(), false);
    myIsConstructor = isConstructor;
  }

  // Test constructor allowing for null PsiElements.
  @VisibleForTesting
  public GradleDslMethodCall(@NotNull GradleDslElement parent,
                             @Nullable PsiElement methodCall,
                             @NotNull GradleNameElement name,
                             boolean isConstructor,
                             @NotNull String methodName) {
    super(parent, methodCall, name, methodCall);
    myMethodName = methodName;
    myArguments = new GradleDslExpressionList(this, GradleNameElement.empty(), false);
    myIsConstructor = isConstructor;
  }

  public void setParsedArgumentList(@NotNull GradleDslExpressionList arguments) {
    myArguments = arguments;
  }

  public void addParsedExpression(@NotNull GradleDslExpression expression) {
    myArguments.addParsedExpression(expression);
  }

  public void addNewArgument(@NotNull GradleDslExpression argument) {
    myArguments.addNewExpression(argument);
  }

  public void replaceArgument(@NotNull GradleDslExpression oldElement, @NotNull GradleDslExpression newElement) {
    myArguments.replaceExpression(oldElement, newElement);
  }

  public boolean isConstructor() {
    return myIsConstructor;
  }

  public void setIsConstructor(boolean isConstructor) {
    myIsConstructor = isConstructor;
  }

  @Nullable
  public PsiElement getArgumentListPsiElement() {
    return myArguments.getPsiElement();
  }

  @NotNull
  public List<GradleDslExpression> getArguments() {
    return myArguments.getExpressions();
  }

  @NotNull
  public GradleDslExpressionList getArgumentsElement() {
    return myArguments;
  }

  @Override
  @NotNull
  public Collection<GradleDslElement> getChildren() {
    return new ArrayList<>(getArguments());
  }

  @Override
  @Nullable
  public Object produceValue() {
    return getValueFromArgList(getArguments());
  }

  @Override
  @Nullable
  public Object produceUnresolvedValue() {
    return getValue();
  }

  @Nullable
  private Object getValueFromArgList(@NotNull List<? extends GradleDslElement> args) {
    // If we only have one argument then just return its value. This allows us to correctly
    // parse functions that are used to set properties.
    if (args.size() == 1 && args.get(0) instanceof GradleDslSimpleExpression) {
      return ((GradleDslSimpleExpression)args.get(0)).getValue();
    }

    PsiElement psiElement = getPsiElement();
    return psiElement != null ? getPsiText(psiElement) : null;
  }

  @Override
  @Nullable
  public <T> T getValue(@NotNull Class<T> clazz) {
    if (clazz.isAssignableFrom(File.class)) {
      return clazz.cast(getFileValue());
    }
    Object value = getValue();
    if (clazz.isInstance(value)) {
      return clazz.cast(value);
    }
    return null;
  }

  @Override
  @Nullable
  public <T> T getUnresolvedValue(@NotNull Class<T> clazz) {
    return getValue(clazz);
  }

  @Nullable
  private File getFileValue() {
    String path = PropertyUtil.getFileValue(this);
    return path == null ? null : new File(path);
  }

  @Override
  public void setValue(@NotNull Object value) {
    if (value instanceof File) {
      setFileValue((File)value);
    }
    // TODO: Add support to set the full method definition as a String.

    valueChanged();
  }

  @Nullable
  @Override
  public Object produceRawValue() {
    return getUnresolvedValue();
  }

  @NotNull
  @Override
  public GradleDslMethodCall copy() {
    assert myParent != null;
    GradleDslMethodCall methodCall = new GradleDslMethodCall(myParent, GradleNameElement.copy(myName), myMethodName);

    for (GradleDslExpression argument : getArguments()) {
      GradleDslExpression copy;
      copy = argument.copy();
      methodCall.addNewArgument(copy);
    }
    return methodCall;
  }

  private void setFileValue(@NotNull File file) {
    if (!myMethodName.equals("file")) {
      LOG.error(new UnsupportedOperationException("Cannot set a file value to a method other than file(). Method name: " + myMethodName));
      return;
    }

    List<GradleDslExpression> arguments = getArguments();
    if (arguments.isEmpty()) {
      GradleDslLiteral argument = new GradleDslLiteral(this, GradleNameElement.empty());
      argument.setValue(file.getPath());
      myArguments.addNewExpression(argument);
      return;
    }

    GradleDslElement pathArgument = arguments.get(0);
    if (!(pathArgument instanceof GradleDslSimpleExpression)) {
      return;
    }

    ((GradleDslSimpleExpression)pathArgument).setValue(file.getPath());
  }

  @NotNull
  public String getMethodName() {
    return myMethodName;
  }

  /**
   * Renames an element which represents a property/dependency level method call and should only me called when renaming the corresponding
   * property/dependency. It has to be called before changing the name of the property/dependency.
   */
  public void setMethodName(@NotNull String newMethodName) {
    myMethodName = newMethodName;
  }

  @Override
  @NotNull
  public String getName() {
    String name = super.getName();
    return name.isEmpty() ? getMethodName() : name;
  }

  public void remove(@NotNull GradleDslElement argument) {
    myArguments.removeElement(argument);
  }

  @Override
  protected void apply() {
    getDslFile().getWriter().applyDslMethodCall(this);
  }

  @Override
  protected void reset() {
    super.reset();
    myArguments.reset();
  }

  @Override
  @Nullable
  public PsiElement create() {
    return getDslFile().getWriter().createDslMethodCall(this);
  }
}
