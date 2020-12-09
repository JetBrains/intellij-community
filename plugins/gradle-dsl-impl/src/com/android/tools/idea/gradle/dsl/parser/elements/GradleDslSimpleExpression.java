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

import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.api.util.GradleNameElementUtil;
import com.android.tools.idea.gradle.dsl.model.CachedValue;
import com.android.tools.idea.gradle.dsl.model.GradleSettingsModelImpl;
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.GradleDslParser;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement;
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleSettingsFile;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.followElement;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.isPropertiesElementOrMap;
import static com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement.BUILDSCRIPT;
import static com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement.EXT;
import static com.android.tools.idea.gradle.dsl.parser.settings.ProjectPropertiesDslElement.getStandardProjectKey;
import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

/**
 * Represents an expression element.
 */
public abstract class GradleDslSimpleExpression extends GradleDslElementImpl implements GradleDslExpression {
  @NotNull private static final String SINGLE_QUOTES = "'";
  @NotNull private static final String DOUBLE_QUOTES = "\"";
  private boolean myIsReference;
  @Nullable private PsiElement myUnsavedConfigBlock;

  @Nullable protected PsiElement myExpression;
  // Whether or not this value is part of a cycle. If UNSURE, needs to be computed.
  @Nullable protected ThreeState myHasCycle;

  @NotNull private final CachedValue<GradleDslSimpleExpression> myResolvedCachedValue;
  @NotNull private final CachedValue<GradleDslSimpleExpression> myUnresolvedCachedValue;
  @NotNull private final CachedValue<GradleDslSimpleExpression> myRawCachedValue;

  protected GradleDslSimpleExpression(@Nullable GradleDslElement parent,
                                      @Nullable PsiElement psiElement,
                                      @NotNull GradleNameElement name,
                                      @Nullable PsiElement expression) {
    super(parent, psiElement, name);
    myExpression = expression;
    myHasCycle = ThreeState.UNSURE;
    resolve();
    // Resolved values must be created after resolve() is called. If the debugger calls toString to trigger
    // any of the producers they will be stuck with the wrong value as dependencies have not been computed.
    myResolvedCachedValue = new CachedValue<>(this, GradleDslSimpleExpression::produceValue);
    myUnresolvedCachedValue = new CachedValue<>(this, GradleDslSimpleExpression::produceUnresolvedValue);
    myRawCachedValue = new CachedValue<>(this, GradleDslSimpleExpression::produceRawValue);
  }

  @Nullable
  public PsiElement getUnsavedConfigBlock() {
    return myUnsavedConfigBlock;
  }

  public void setUnsavedConfigBlock(@Nullable PsiElement configBlock) {
    myUnsavedConfigBlock = configBlock;
  }

  public void setConfigBlock(@NotNull PsiElement block) {
    // For now we only support setting the config block on literals for newly created dependencies.
    Preconditions.checkState(getPsiElement() == null, "Can't add configuration block to an existing DSL literal.");

    // TODO: Use com.android.tools.idea.gradle.dsl.parser.dependencies.DependencyConfigurationDslElement to add a dependency configuration.

    myUnsavedConfigBlock = block;
    setModified();
  }


  @Override
  @Nullable
  public PsiElement getExpression() {
    return myExpression;
  }

  public void setExpression(@NotNull PsiElement expression) {
    myExpression = expression;
  }

  @Nullable
  public final Object getValue() {
    return myResolvedCachedValue.getValue();
  }

  @Nullable
  protected abstract Object produceValue();

  @Nullable
  public final Object getUnresolvedValue() {
    return myUnresolvedCachedValue.getValue();
  }

  @Nullable
  protected abstract Object produceUnresolvedValue();

  @Nullable
  public <T> T getValue(@NotNull Class<T> clazz) {
    Object value = getValue();
    if (value != null && clazz.isAssignableFrom(value.getClass())) {
      return clazz.cast(value);
    }
    return null;
  }

  @Nullable
  public <T> T getUnresolvedValue(@NotNull Class<T> clazz) {
    Object value = getUnresolvedValue();
    if (value != null && clazz.isAssignableFrom(value.getClass())) {
      return clazz.cast(value);
    }
    return null;
  }

  public abstract void setValue(@NotNull Object value);

  @Nullable
  public final Object getRawValue() {
    return myRawCachedValue.getValue();
  }

  /**
   * @return an object representing the raw value, this can be passed into {@link GradlePropertyModel#setValue(Object)} to set the value
   * correctly. That is, this method will return a {@link ReferenceTo} if needed where as {@link #getUnresolvedValue()} will not.
   * It will also correctly wrap any string that should be interpolated with double quotes.
   */
  @Nullable
  protected abstract Object produceRawValue();

  /**
   * @return a new object that is based on this one but has no backing PsiElement or parent.
   * This means that it can be used to duplicate the element and use it elsewhere in the tree without the danger that the PsiElement will
   * be deleted from use elsewhere.
   */
  @Override
  @NotNull
  public abstract GradleDslSimpleExpression copy();

  /**
   * This should be overridden by subclasses if they require different behaviour, such as getting the dependencies of
   * un-applied expressions.
   */
  @Override
  @NotNull
  public List<GradleReferenceInjection> getResolvedVariables() {
    return myDependencies.stream().filter(e -> e.isResolved()).collect(Collectors.toList());
  }

  public boolean isReference() {
    return myIsReference;
  }

  public void setReference(boolean isReference) {
    myIsReference = isReference;
  }

  @Nullable
  public String getReferenceText() {
    return null; // Overridden by subclasses.
  }

  @Override
  protected void reset() {
    myRawCachedValue.clear();
    myUnresolvedCachedValue.clear();
    myResolvedCachedValue.clear();
  }

  @Nullable
  public GradleDslElement resolveExternalSyntaxReference(@NotNull String referenceText, boolean resolveWithOrder) {
    GradleDslElement searchStartElement = this;
    GradleDslParser parser = getDslFile().getParser();
    referenceText = parser.convertReferenceText(searchStartElement, referenceText);

    return resolveInternalSyntaxReference(referenceText, resolveWithOrder);
  }

  @Nullable
  public GradleDslElement resolveInternalSyntaxReference(@NotNull String referenceText, boolean resolveWithOrder) {
    GradleDslElement searchStartElement = this;
    GradleDslParser parser = getDslFile().getParser();

    boolean withinBuildscript = false;
    GradleDslElement element = this;
    while (element != null) {
      element = element.getParent();
      if (element instanceof BuildScriptDslElement) {
        withinBuildscript = true;
        break;
      }
    }

    List<String> referenceTextSegments = GradleNameElementUtil.split(referenceText);
    int index = 0;
    int segmentCount = referenceTextSegments.size();
    for (; index < segmentCount; index++) {
      // Resolve the project reference elements like parent, rootProject etc.
      GradleDslFile dslFile = resolveProjectReference(searchStartElement, referenceTextSegments.get(index));
      if (dslFile == null) {
        break;
      }
      // start the search for our element at the top-level of the Dsl file (but see below for buildscript handling)
      searchStartElement = dslFile;
    }

    /* For a project with the below hierarchy ...

    | <GRADLE_USER_HOME>/gradle.properties
    | RootProject
    | - - build.gradle
    | - - gradle.properties
    | - - FirstLevelChildProject
    | - - - - build.gradle
    | - - - - gradle.properties
    | - - - - SecondLevelChildProject
    | - - - - - - build.gradle
    | - - - - - - gradle.properties
    | - - - - - - ThirdLevelChildProject
    | - - - - - - - - build.gradle
    | - - - - - - - - gradle.properties

    the resolution path for a property defined in ThirdLevelChildProject's build.gradle file will be ...

      1. ThirdLevelChildProject/build.gradle
      2. <GRADLE_USER_HOME>/gradle.properties
      3. ThirdLevelChildProject/gradle.properties
      4. RootProject/gradle.properties
      5. SecondLevelChildProject/build.gradle
      6. SecondLevelChildProject/gradle.properties
      7. FirstLevelChildProject/build.gradle
      8. FirstLevelChildProject/gradle.properties
      9. RootProject/build.gradle
    */

    GradleDslElement resolvedElement = null;
    GradleDslFile dslFile = searchStartElement.getDslFile();
    if (index >= segmentCount) {
      // the reference text is fully resolved by now. ex: if the while text itself is "rootProject" etc.
      resolvedElement = searchStartElement;
    }
    else {
      // Search in the file that searchStartElement belongs to.
      referenceTextSegments = referenceTextSegments.subList(index, segmentCount);
      // if we are resolving in the general context of buildscript { } within the same module, then build code external to the
      // buildscript block will not yet have run: restrict search to the buildscript element (which should exist)
      if (dslFile == searchStartElement  && withinBuildscript) {
        searchStartElement = dslFile.getPropertyElement(BUILDSCRIPT);
      }
      if (searchStartElement != null) {
        resolvedElement = resolveReferenceInSameModule(searchStartElement, referenceTextSegments, parser, resolveWithOrder);
      }
    }

    if (resolvedElement == null) {
      // Now look in the parent projects ext blocks.
      resolvedElement = resolveReferenceInParentModules(dslFile, referenceTextSegments, parser);
    }

    return resolvedElement;
  }

  @Nullable
  private static GradleDslFile resolveProjectReference(GradleDslElement startElement, @NotNull String projectReference) {
    GradleDslFile dslFile = startElement.getDslFile();
    if ("project".equals(projectReference)) {
      return dslFile;
    }

    if ("parent".equals(projectReference)) {
      return dslFile.getParentModuleDslFile();
    }

    if ("rootProject".equals(projectReference)) {
      while (dslFile != null && !filesEqual(dslFile.getDirectoryPath(), virtualToIoFile(dslFile.getProject().getBaseDir()))) {
        dslFile = dslFile.getParentModuleDslFile();
      }
      return dslFile;
    }

    String standardProjectKey = getStandardProjectKey(projectReference);
    if (standardProjectKey != null) { // project(':project:path')
      String modulePath = standardProjectKey.substring(standardProjectKey.indexOf('\'') + 1, standardProjectKey.lastIndexOf('\''));
      VirtualFile settingFile = dslFile.tryToFindSettingsFile();
      if (settingFile == null) {
        return null;
      }
      GradleSettingsFile file = dslFile.getContext().getOrCreateSettingsFile(settingFile);
      GradleSettingsModel model = new GradleSettingsModelImpl(file);
      File moduleDirectory = model.moduleDirectory(modulePath);
      if (moduleDirectory == null) {
        return null;
      }
      while (dslFile != null && !filesEqual(dslFile.getDirectoryPath(), virtualToIoFile(dslFile.getProject().getBaseDir()))) {
        dslFile = dslFile.getParentModuleDslFile();
      }
      if (dslFile == null) {
        return null;
      }
      return findDslFile(dslFile, moduleDirectory); // root module dsl File.
    }
    return null;
  }

  @NotNull
  private static String stripQuotes(@NotNull String index) {
    if (index.startsWith(SINGLE_QUOTES) && index.endsWith(SINGLE_QUOTES) ||
        index.startsWith(DOUBLE_QUOTES) && index.endsWith(DOUBLE_QUOTES)) {
      return index.substring(1, index.length() - 1);
    }
    return index;
  }

  @Nullable
  public static GradleDslElement dereference(@NotNull GradleDslElement element, @NotNull String index) {
    if (element instanceof GradleDslExpressionList) {
      int offset;
      try {
        offset = Integer.parseInt(index);
      }
      catch (NumberFormatException e) {
        return null;
      }

      GradleDslExpressionList list = (GradleDslExpressionList)element;
      if (list.getExpressions().size() <= offset) {
        return null;
      }
      return list.getExpressions().get(offset);
    }
    else if (element instanceof GradleDslExpressionMap) {
      GradleDslExpressionMap map = (GradleDslExpressionMap)element;
      index = stripQuotes(index);

      return map.getPropertyElement(index);
    }
    else if (element instanceof GradleDslLiteral && ((GradleDslLiteral)element).isReference()) {
      GradleDslElement value = followElement((GradleDslLiteral)element);
      if (value == null) {
        return null;
      }
      else {
        return dereference(value, index);
      }
    }
    else {
      return null;
    }
  }

  /**
   * This is like plain {@link #dereference(GradleDslElement, String)} but with the opposite handling of references from DslLiterals: its
   * input must already be resolved to a PropertiesDslElement, and it follows references from DslLiterals to return a PropertiesDslElement,
   * which is particularly useful when we require the return value itself to be dereferenceable (e.g. for assignments)
   *
   * @param element
   * @param index
   * @return
   */
  @Nullable
  public static GradlePropertiesDslElement dereferencePropertiesElement(@NotNull GradlePropertiesDslElement element, @NotNull String index) {
    GradleDslElement result = dereference(element, index);
    if (result instanceof GradleDslLiteral && ((GradleDslLiteral)result).isReference()) {
      result = followElement((GradleDslLiteral)result);
    }
    if (result instanceof GradlePropertiesDslElement) {
      return (GradlePropertiesDslElement)result;
    }
    else {
      return null;
    }
  }

  @Nullable
  private static GradleDslElement extractElementFromProperties(@NotNull GradlePropertiesDslElement properties,
                                                               @NotNull String name,
                                                               GradleDslNameConverter converter,
                                                               boolean sameScope,
                                                               @Nullable GradleDslElement childElement,
                                                               boolean includeSelf) {

    // First check if any indexing has been done.
    Matcher indexMatcher = GradleNameElement.INDEX_PATTERN.matcher(name);

    // If the index matcher doesn't give us anything, just attempt to find the property on the element;
    if (!indexMatcher.find()) {
      ModelPropertyDescription property = converter.modelDescriptionForParent(name, properties);
      String modelName = property == null ? name : property.name;

      return sameScope
             ? properties.getElementBefore(childElement, modelName, includeSelf)
             : properties.getPropertyElementBefore(childElement, modelName, includeSelf);
    }

    // Sanity check
    if (indexMatcher.groupCount() != 2) {
      return null;
    }

    // We have some index present, find the element we need to index. The first match, the property, is always the whole match.
    String elementName = indexMatcher.group(0);
    if (elementName == null) {
      return null;
    }
    ModelPropertyDescription property = converter.modelDescriptionForParent(elementName, properties);
    String modelName = property == null ? elementName : property.name;

    GradleDslElement element =
      sameScope
      ? properties.getElementBefore(childElement, modelName, includeSelf)
      : properties.getPropertyElementBefore(childElement, modelName, includeSelf);

    // Construct a list of all of the index parts
    Deque<String> indexParts = new ArrayDeque<>();
    while (indexMatcher.find()) {
      // Sanity check
      if (indexMatcher.groupCount() != 2) {
        return null;
      }
      // second and subsequent matches of INDEX_PATTERN have .group(0) being "[...]", and .group(1) the text inside the brackets.
      indexParts.add(indexMatcher.group(1));
    }

    // Go through each index and search for the element.
    while (!indexParts.isEmpty()) {
      String index = indexParts.pop();
      // Ensure the element is not null
      if (element == null) {
        return null;
      }

      // Get the type of the element and ensure the index is compatible, e.g numerical index for a list.
      element = dereference(element, index);
    }

    return element;
  }

  @Nullable
  private static GradleDslElement resolveReferenceOnPropertiesElement(@NotNull GradlePropertiesDslElement properties,
                                                                      @NotNull List<String> nameParts,
                                                                      GradleDslNameConverter converter,
                                                                      @NotNull List<GradleDslElement> trace) {
    int traceIndex = trace.size() - 1;
    // Go through each of the parts and extract the elements from each of them.
    GradleDslElement element;
    for (int i = 0; i < nameParts.size() - 1; i++) {
      // Only look for variables on the first iteration, otherwise only properties should be accessible.
      element = extractElementFromProperties(properties, nameParts.get(i), converter, i == 0, traceIndex < 0 ? null : trace.get(traceIndex--),
                                             traceIndex >= 0);
      if (element instanceof GradleDslLiteral && ((GradleDslLiteral)element).isReference()) {
        element = followElement((GradleDslLiteral)element);
      }

      // All elements we find must be GradlePropertiesDslElement on all but the last iteration.
      if (!isPropertiesElementOrMap(element)) {
        return null;
      }
      // isPropertiesElementOrMap should always return false when is not an instance of GradlePropertiesDslElement.
      //noinspection ConstantConditions
      properties = (GradlePropertiesDslElement)element;
    }

    return extractElementFromProperties(properties, nameParts.get(nameParts.size() - 1), converter, nameParts.size() == 1,
                                        traceIndex < 0 ? null : trace.get(traceIndex--), traceIndex >= 0);
  }

  @Nullable
  private static GradleDslElement resolveReferenceOnElement(@NotNull GradleDslElement element,
                                                            @NotNull List<String> nameParts,
                                                            GradleDslNameConverter converter,
                                                            boolean resolveWithOrder,
                                                            boolean checkExt,
                                                            int ignoreParentNumber) {
    // We need to keep track of the last element we saw to ensure we only check items BEFORE the one we are resolving.
    Stack<GradleDslElement> elementTrace = new Stack<>();
    if (resolveWithOrder) {
      elementTrace.push(element);
    }
    // Make sure we don't check any nested scope for the element.
    while (ignoreParentNumber-- > 0 && element != null && !(element instanceof GradleDslFile) && !(element instanceof BuildScriptDslElement)) {
      element = element.getParent();
    }
    while (element != null) {
      GradleDslElement lastElement = elementTrace.isEmpty() ? null : elementTrace.peek();
      if (isPropertiesElementOrMap(element)) {
        GradleDslElement propertyElement = resolveReferenceOnPropertiesElement((GradlePropertiesDslElement)element, nameParts,
                                                                               converter, elementTrace);
        if (propertyElement != null) {
          return propertyElement;
        }

        // If it is then we have already checked the ExtElement of this object.
        if (!(lastElement instanceof ExtDslElement) && checkExt) {
          GradleDslElement extElement =
            ((GradlePropertiesDslElement)element).getPropertyElementBefore(lastElement, EXT.name, false);
          if (extElement instanceof ExtDslElement) {
            GradleDslElement extPropertyElement =
              resolveReferenceOnPropertiesElement((ExtDslElement)extElement, nameParts, converter, elementTrace);
            if (extPropertyElement != null) {
              return extPropertyElement;
            }
          }
        }

        if (!(lastElement instanceof BuildScriptDslElement)) {
          GradleDslElement bsDslElement =
            ((GradlePropertiesDslElement)element).getPropertyElementBefore(element, BUILDSCRIPT.name, false);
          if (bsDslElement instanceof BuildScriptDslElement) {
            GradleDslElement bsElement =
              resolveReferenceOnElement(bsDslElement, nameParts, converter, true /* Must be true or we just jump between buildscript -> parent */,
                                        false, -1);
            if (bsElement != null) {
              return bsElement;
            }
          }
        }
      }

      if (resolveWithOrder) {
        elementTrace.push(element);
      }

      // Don't resolve up the parents for BuildScript elements.
      if (element instanceof BuildScriptDslElement) {
        return null;
      }
      element = element.getParent();
    }

    return null;
  }

  @Nullable
  private static GradleDslElement resolveReferenceInSameModule(@NotNull GradleDslElement startElement,
                                                               @NotNull List<String> referenceText,
                                                               GradleDslNameConverter converter,
                                                               boolean resolveWithOrder) {
    // Try to resolve in the build.gradle file the startElement belongs to.
    GradleDslElement element =
      resolveReferenceOnElement(startElement, referenceText, converter, resolveWithOrder, true, startElement.getNameElement().fullNameParts().size());
    if (element != null) {
      return element;
    }

    // Join the text before looking in the properties files.
    String text = String.join(".", referenceText);

    // TODO: Add support to look at <GRADLE_USER_HOME>/gradle.properties before looking at this module's gradle.properties file.

    // Try to resolve in the gradle.properties file of the startElement's module.
    GradleDslFile dslFile = startElement.getDslFile();
    GradleDslElement propertyElement = resolveReferenceInPropertiesFile(dslFile, text);
    if (propertyElement != null) {
      return propertyElement;
    }

    // Ensure we check the buildscript as well.
    BuildScriptDslElement bsDslElement = dslFile.getPropertyElement(BUILDSCRIPT);
    if (bsDslElement != null) {
      GradleDslElement bsElement = resolveReferenceOnElement(bsDslElement, referenceText, converter, false, true, -1);
      if (bsElement != null) {
        return bsElement;
      }
    }


    if (dslFile.getParentModuleDslFile() == null) {
      return null; // This is the root project build.gradle file and there is no further path to look up.
    }

    // Try to resolve in the root project gradle.properties file.
    GradleDslFile rootProjectDslFile = dslFile;
    while (true) {
      GradleDslFile parentModuleDslFile = rootProjectDslFile.getParentModuleDslFile();
      if (parentModuleDslFile == null) {
        break;
      }
      rootProjectDslFile = parentModuleDslFile;
    }
    return resolveReferenceInPropertiesFile(rootProjectDslFile, text);
  }

  @Nullable
  private static GradleDslElement resolveReferenceInParentModules(
    @NotNull GradleDslFile dslFile,
    @NotNull List<String> referenceText,
    GradleDslNameConverter converter
  ) {
    GradleDslFile parentDslFile = dslFile.getParentModuleDslFile();
    while (parentDslFile != null) {
      ExtDslElement extDslElement = parentDslFile.getPropertyElement(EXT);
      if (extDslElement != null) {
        GradleDslElement extPropertyElement = resolveReferenceOnPropertiesElement(extDslElement, referenceText, converter, new Stack<>());
        if (extPropertyElement != null) {
          return extPropertyElement;
        }
      }

      BuildScriptDslElement bsDslElement = parentDslFile.getPropertyElement(BUILDSCRIPT);
      if (bsDslElement != null) {
        GradleDslElement bsElement = resolveReferenceOnElement(bsDslElement, referenceText, converter, false, true, -1);
        if (bsElement != null) {
          return bsElement;
        }
      }

      if (parentDslFile.getParentModuleDslFile() == null) {
        // This is the root project build.gradle file and the root project's gradle.properties file is already considered in
        // resolveReferenceInSameModule method.
        return null;
      }

      GradleDslElement propertyElement = resolveReferenceInPropertiesFile(parentDslFile, String.join(".", referenceText));
      if (propertyElement != null) {
        return propertyElement;
      }

      parentDslFile = parentDslFile.getParentModuleDslFile();
    }
    return null;
  }

  @Nullable
  private static GradleDslElement resolveReferenceInPropertiesFile(@NotNull GradleDslFile buildDslFile, @NotNull String referenceText) {
    GradleDslFile propertiesDslFile = buildDslFile.getSiblingDslFile();
    return propertiesDslFile != null ? propertiesDslFile.getPropertyElement(referenceText) : null;
  }

  @Nullable
  private static GradleDslFile findDslFile(GradleDslFile rootModuleDslFile, File moduleDirectory) {
    if (filesEqual(rootModuleDslFile.getDirectoryPath(), moduleDirectory)) {
      return rootModuleDslFile;
    }

    for (GradleDslFile dslFile : rootModuleDslFile.getChildModuleDslFiles()) {
      if (filesEqual(dslFile.getDirectoryPath(), moduleDirectory)) {
        return dslFile;
      }
      GradleDslFile childDslFile = findDslFile(dslFile, moduleDirectory);
      if (childDslFile != null) {
        return dslFile;
      }
    }
    return null;
  }

  /**
   * Tells the expression that the value has changed, this sets this element to modified and resets the cycle detection state.
   */
  protected void valueChanged() {
    myHasCycle = ThreeState.UNSURE;
    setModified();
  }

  /**
   * Works out whether or not this GradleDslSimpleExpression has a cycle.
   */
  public boolean hasCycle() {
    if (myHasCycle != ThreeState.UNSURE) {
      return myHasCycle == ThreeState.YES;
    }
    return hasCycle(this, new HashSet<>(), new HashSet<>());
  }

  private static boolean hasCycle(@NotNull GradleDslSimpleExpression element,
                                  @NotNull Set<GradleDslSimpleExpression> seen,
                                  @NotNull Set<GradleDslSimpleExpression> cycleFree) {
    if (element.myHasCycle != ThreeState.UNSURE) {
      return element.myHasCycle == ThreeState.YES;
    }

    boolean hasCycle = checkCycle(element, seen, cycleFree);
    element.myHasCycle = hasCycle ? ThreeState.YES : ThreeState.NO;
    return hasCycle;
  }

  private static boolean checkCycle(@NotNull GradleDslSimpleExpression element,
                                    @NotNull Set<GradleDslSimpleExpression> seen,
                                    @NotNull Set<GradleDslSimpleExpression> cycleFree) {
    if (cycleFree.contains(element) || element.getExpression() == null) {
      return false;
    }

    if (seen.contains(element)) {
      return true;
    }

    seen.add(element);

    Collection<GradleReferenceInjection> injections = element.getResolvedVariables();

    for (GradleReferenceInjection injection : injections) {
      if (injection.getToBeInjectedExpression() == null) {
        continue;
      }

      boolean hasCycle = hasCycle(injection.getToBeInjectedExpression(), seen, cycleFree);
      if (hasCycle) {
        seen.remove(element);
        return true;
      }
    }

    seen.remove(element);
    cycleFree.add(element);
    return false;
  }

  @Override
  public void resolve() {
    setupDependencies(myExpression);
  }

  @NotNull
  protected List<GradleReferenceInjection> fetchDependencies(@Nullable PsiElement element) {
    if (element == null) {
      return ImmutableList.of();
    }
    return ApplicationManager.getApplication()
                             .runReadAction(
                               (Computable<List<GradleReferenceInjection>>)() -> getDslFile().getParser().getInjections(this, element));
  }

  protected void setupDependencies(@Nullable PsiElement element) {
    // Unregister any registered dependencies.
    myDependencies.stream().filter(e -> e.getToBeInjected() != null).forEach(e -> e.getToBeInjected().unregisterDependent(e));
    myDependencies.stream().filter(e -> e.getToBeInjected() == null)
                  .forEach(e -> getDslFile().getContext().getDependencyManager().unregisterUnresolvedReference(e));
    myDependencies.clear();
    myDependencies.addAll(fetchDependencies(element));
    // Register any resolved dependencies with the elements they depend on.
    myDependencies.stream().filter(e -> e.getToBeInjected() != null).forEach(e -> e.getToBeInjected().registerDependent(e));
    myDependencies.stream().filter(e -> e.getToBeInjected() == null)
                  .forEach(e -> getDslFile().getContext().getDependencyManager().registerUnresolvedReference(e));
  }
}
