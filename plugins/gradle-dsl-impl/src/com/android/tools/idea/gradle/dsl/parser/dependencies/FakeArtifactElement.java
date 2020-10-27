/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.dependencies;

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpecImpl;
import com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.google.common.collect.ImmutableList;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.iStr;
import static com.intellij.openapi.util.text.StringUtil.isQuotedString;
import static com.intellij.openapi.util.text.StringUtil.unquoteString;

/**
 * FakeElement representing part of an artifact in compact notation. This allows us to represent each component as its own property.
 * The actual element in the tree that the compact notation is represented by is a {@link GradleDslLiteral} with a value
 * such as 'com.android.support:appcompat-v7:22.1.1'. However for the model api we need to be able to treat each component as
 * its own property to provide a consistency with the map based form.
 */
public class FakeArtifactElement extends FakeElement {
  @NotNull private final Function<ArtifactDependencySpec, String> myGetter;
  @NotNull private final BiConsumer<ArtifactDependencySpecImpl, String> mySetter;

  @NotNull private static final Pattern WRAPPED_VARIABLE_FORM = Pattern.compile("\\$\\{(.*)}");
  @NotNull private static final Pattern UNWRAPPED_VARIABLE_FORM = Pattern.compile("\\$(([a-zA-Z]\\w*)(\\.([a-zA-Z]\\w+))*)");

  public static boolean shouldInterpolate(@Nullable String str) {
    return str != null && (WRAPPED_VARIABLE_FORM.matcher(str).matches() || UNWRAPPED_VARIABLE_FORM.matcher(str).matches());
  }

  public FakeArtifactElement(@Nullable GradleDslElement parent,
                             @NotNull GradleNameElement name,
                             @NotNull GradleDslSimpleExpression originExpression,
                             @NotNull Function<ArtifactDependencySpec, String> getFunc,
                             @NotNull BiConsumer<ArtifactDependencySpecImpl, String> setFunc,
                             boolean canDelete) {
    super(parent, name, originExpression, canDelete);
    myGetter = getFunc;
    mySetter = setFunc;
  }

  @Override
  @Nullable
  public Object extractValue() {
    GradleDslSimpleExpression resolved = PropertyUtil.resolveElement(myRealExpression);
    ArtifactDependencySpec spec = getSpec(resolved);
    if (spec == null) {
      return null;
    }

    if (resolved.getDslFile().getParser().shouldInterpolate(resolved)) {
      String result = myGetter.apply(spec);
      return result == null ? null : iStr(result);
    }
    else {
      return myGetter.apply(spec);
    }
  }

  @Override
  protected void consumeValue(@Nullable Object value) {
    assert myCanDelete || value != null;
    GradleDslSimpleExpression resolved = PropertyUtil.resolveElement(myRealExpression);
    ArtifactDependencySpecImpl spec = getSpec(resolved);
    if (spec == null) {
      throw new IllegalArgumentException("Could not create ArtifactDependencySpec from: " + value);
    }
    assert value instanceof String || value instanceof ReferenceTo || value == null;
    boolean shouldQuote = false;
    String strValue = null;
    if (value instanceof ReferenceTo) {
      strValue = "${" + resolved.getDslFile().getParser().convertReferenceToExternalText(resolved, ((ReferenceTo)value).getText(), true) + "}";
      shouldQuote = true;
    }
    else if (value != null) {
      strValue = (String)value;
      if (isDoubleQuotedString(strValue)) {
        shouldQuote = true;
        strValue = unquoteString((String)value);
      }
    }
    mySetter.accept(spec, strValue);
    if (shouldQuote ||
        resolved.getDslFile().getParser().shouldInterpolate(resolved)) {
      myRealExpression.setValue(iStr(spec.compactNotation()));
    }
    else {
      myRealExpression.setValue(spec.compactNotation());
    }
  }

  private static boolean isDoubleQuotedString(@NotNull String str) {
    return isQuotedString(str) && str.charAt(0) == '\"';
  }

  @Nullable
  @Override
  public Object produceRawValue() {
    return getUnresolvedValue();
  }

  @NotNull
  @Override
  public GradleDslSimpleExpression copy() {
    return new FakeArtifactElement(myParent, GradleNameElement.copy(myFakeName), myRealExpression, myGetter, mySetter, myCanDelete);
  }

  @NotNull
  @Override
  public List<GradleReferenceInjection> getResolvedVariables() {
    PsiElement realExpression;
    if (myRealExpression instanceof GradleDslSettableExpression) {
      realExpression = ((GradleDslSettableExpression)myRealExpression).getCurrentElement();
    }
    else {
      realExpression = myRealExpression.getExpression();
    }

    if (realExpression == null) {
      return ImmutableList.of();
    }

    String referenceText = getReferenceText();
    if (referenceText == null) {
      return ImmutableList.of();
    }

    GradleDslSimpleExpression resolved = PropertyUtil.resolveElement(myRealExpression);
    GradleDslElement element = resolved.resolveExternalSyntaxReference(referenceText, true);
    return ImmutableList.of(new GradleReferenceInjection(this, element, realExpression /* Used as a placeholders */, referenceText));
  }

  @NotNull
  @Override
  public List<GradleReferenceInjection> getDependencies() {
    return getResolvedVariables();
  }

  @Override
  public boolean isReference() {
    GradleDslSimpleExpression resolved = PropertyUtil.resolveElement(myRealExpression);
    ArtifactDependencySpec spec = getSpec(resolved, false);
    if (spec == null) {
      return false;
    }
    String result = myGetter.apply(spec);
    if (result != null && (WRAPPED_VARIABLE_FORM.matcher(result).matches() || UNWRAPPED_VARIABLE_FORM.matcher(result).matches())) {
      return true;
    }

    return false;
  }

  @Nullable
  @Override
  public String getReferenceText() {
    GradleDslSimpleExpression resolved = PropertyUtil.resolveElement(myRealExpression);
    ArtifactDependencySpec spec = getSpec(resolved, false);
    if (spec == null) {
      return null;
    }
    String result = myGetter.apply(spec);
    if (result == null) {
      return null;
    }
    if (result.startsWith("${")) {
      Matcher m = WRAPPED_VARIABLE_FORM.matcher(result);
      if (!m.matches() || m.groupCount() < 1) {
        return null;
      }
      return m.group(1);
    }
    else {
      Matcher m = UNWRAPPED_VARIABLE_FORM.matcher(result);
      if (!m.matches() || m.groupCount() < 1) {
        return null;
      }
      return m.group(1);
    }
  }

  @Nullable
  private static ArtifactDependencySpecImpl getSpec(@NotNull GradleDslSimpleExpression element) {
    return getSpec(element, true);
  }

  @Nullable
  private static ArtifactDependencySpecImpl getSpec(@NotNull GradleDslSimpleExpression element, boolean useResolvedValue) {
    Object val = (useResolvedValue) ? element.getValue() : element.getUnresolvedValue();
    assert val instanceof String;
    String stringValue = (String)val;
    return ArtifactDependencySpecImpl.create(stringValue);
  }
}
