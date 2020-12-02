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
package com.android.tools.idea.gradle.dsl.model.dependencies;

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyConfigurationModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.FakeElementTransform;
import com.android.tools.idea.gradle.dsl.parser.dependencies.FakeArtifactElement;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.google.common.collect.Lists;
import com.intellij.psi.PsiElement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.NONE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.iStr;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.*;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.resolveElement;

/**
 * A Gradle artifact dependency. There are two notations supported for declaring a dependency on an external module. One is a string
 * notation formatted this way:
 * <pre>
 * configurationName "group:name:version:classifier@extension"
 * </pre>
 * The other is a map notation:
 * <pre>
 * configurationName group: group:, name: name, version: version, classifier: classifier, ext: extension
 * </pre>
 * For more details, visit:
 * <ol>
 * <li><a href="https://docs.gradle.org/2.4/userguide/dependency_management.html">Gradle Dependency Management</a></li>
 * <li><a href="https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.dsl.DependencyHandler.html">Gradle
 * DependencyHandler</a></li>
 * </ol>
 */
public abstract class ArtifactDependencyModelImpl extends DependencyModelImpl implements
                                                                              ArtifactDependencyModel {
  @Nullable private GradleDslClosure myConfigurationElement;
  protected boolean mySetThrough = false;

  @NotNull private static final Pattern WRAPPED_VARIABLE_FORM = Pattern.compile("\\$\\{(.*)}");
  @NotNull private static final Pattern UNWRAPPED_VARIABLE_FORM = Pattern.compile("\\$(([a-zA-Z0-9_]\\w*)(\\.([a-zA-Z0-9_]\\w+))*)");


  public ArtifactDependencyModelImpl(@Nullable GradleDslClosure configurationElement,
                                     @NotNull String configurationName,
                                     @NotNull Maintainer maintainer) {
    super(configurationName, maintainer);
    myConfigurationElement = configurationElement;
  }

  @NotNull
  @Override
  public ArtifactDependencySpec getSpec() {
    String name = name().toString();
    assert name != null;
    return new ArtifactDependencySpecImpl(name,
                                          group().toString(),
                                          version().toString(),
                                          classifier().toString(),
                                          extension().toString());
  }

  @Override
  @NotNull
  public String compactNotation() {
    return getSpec().compactNotation();
  }

  @Override
  @NotNull
  public abstract ResolvedPropertyModel name();

  @Override
  @NotNull
  public abstract ResolvedPropertyModel group();

  @Override
  @NotNull
  public abstract ResolvedPropertyModel version();

  @Override
  @NotNull
  public abstract ResolvedPropertyModel classifier();

  @Override
  @NotNull
  public abstract ResolvedPropertyModel extension();

  @Override
  @NotNull
  public abstract ResolvedPropertyModel completeModel();

  @Override
  @Nullable
  public DependencyConfigurationModel configuration() {
    if (myConfigurationElement == null) {
      return null;
    }
    return new DependencyConfigurationModelImpl(myConfigurationElement);
  }

  @Override
  public void enableSetThrough() {
    mySetThrough = true;
  }

  @Override
  public void disableSetThrough() {
    mySetThrough = false;
  }

  static void createNew(@NotNull GradlePropertiesDslElement parent,
                        @NotNull String configurationName,
                        @NotNull ArtifactDependencySpec dependency,
                        @NotNull List<ArtifactDependencySpec> excludes) {
    GradleNameElement name = GradleNameElement.create(configurationName);
    GradleDslLiteral literal = new GradleDslLiteral(parent, name);
    literal.setElementType(REGULAR);
    literal.setValue(createCompactNotationForLiterals(literal, dependency));

    if (!excludes.isEmpty()) {
      PsiElement configBlock = parent.getDslFile().getParser().convertToExcludesBlock(excludes);
      assert configBlock != null;
      literal.setConfigBlock(configBlock);
    }

    parent.setNewElement(literal);
  }

  /**
   * @return same as {@link ArtifactDependencySpec#compactNotation} but quoted if interpolation is needed.
   */
  @NotNull
  private static String createCompactNotationForLiterals(@NotNull GradleDslElement dslElement, @NotNull ArtifactDependencySpec spec) {
    List<String> segments =
      Lists.newArrayList(spec.getGroup(), spec.getName(), spec.getVersion(), spec.getClassifier(), spec.getExtension());
    boolean shouldInterpolate  = false;

    // TODO(b/148283067): this is a workaround to use the correct syntax when creating literals with interpolation.
    StringBuilder compactNotation = new StringBuilder();
    for (int currentElementIdx = 0; currentElementIdx < segments.size(); currentElementIdx++) {
      String segment = segments.get(currentElementIdx);
      if (segment != null) {
        if (currentElementIdx == 4) compactNotation.append("@");
        else if (currentElementIdx > 0) compactNotation.append(":");
        if (FakeArtifactElement.shouldInterpolate(segment)) {
          shouldInterpolate = true;
          Matcher wrappedValueMatcher = WRAPPED_VARIABLE_FORM.matcher(segment);
          Matcher unwrappedValueMatcher = UNWRAPPED_VARIABLE_FORM.matcher(segment);
          String interpolatedVariable = null;
          if (wrappedValueMatcher.find()) {
            interpolatedVariable = wrappedValueMatcher.group(1);
          } else if (unwrappedValueMatcher.find()) {
            interpolatedVariable = unwrappedValueMatcher.group(1);
          }

          String value = interpolatedVariable != null ?
                          dslElement.getDslFile().getParser().convertReferenceToExternalText(dslElement, interpolatedVariable, true)
                                                      : segment;
          // If we have a simple value (i.e. one word) then we don't need to use {} for the injection.
          if (Pattern.compile("([a-zA-Z0-9_]\\w*)").matcher(value).matches()) {
            compactNotation.append("$" + value);
          } else {
            compactNotation.append("${" + value + "}");
          }
          continue;
        }
        compactNotation.append(segment);
      }
    }
    ArtifactDependencySpec dependencySpec = ArtifactDependencySpecImpl.create(compactNotation.toString());

    return shouldInterpolate ? iStr(dependencySpec.compactNotation()) : dependencySpec.compactNotation();
  }

  static final class MapNotation extends ArtifactDependencyModelImpl {
    @NotNull private GradleDslExpressionMap myDslElement;

    @Nullable
    static MapNotation create(@NotNull String configurationName,
                              @NotNull GradleDslExpressionMap dslElement,
                              @Nullable GradleDslClosure configurationElement,
                              @NotNull Maintainer maintainer) {
      if (dslElement.getLiteral("name", String.class) == null) {
        return null; // not a artifact dependency element.
      }

      return new MapNotation(configurationName, dslElement, configurationElement, maintainer);
    }

    private MapNotation(@NotNull String configurationName,
                        @NotNull GradleDslExpressionMap dslElement,
                        @Nullable GradleDslClosure configurationElement,
                        @NotNull Maintainer maintainer) {
      super(configurationElement, configurationName, maintainer);
      myDslElement = dslElement;
    }

    @Override
    @NotNull
    public ResolvedPropertyModel name() {
      return GradlePropertyModelBuilder.create(myDslElement, "name").buildResolved();
    }

    @Override
    @NotNull
    public ResolvedPropertyModel group() {
      return GradlePropertyModelBuilder.create(myDslElement, "group").buildResolved();
    }

    @Override
    @NotNull
    public ResolvedPropertyModel version() {
      return GradlePropertyModelBuilder.create(myDslElement, "version").buildResolved();
    }

    @Override
    @NotNull
    public ResolvedPropertyModel classifier() {
      return GradlePropertyModelBuilder.create(myDslElement, "classifier").buildResolved();
    }

    @Override
    @NotNull
    public ResolvedPropertyModel extension() {
      return GradlePropertyModelBuilder.create(myDslElement, "ext").buildResolved();
    }

    @NotNull
    @Override
    public ResolvedPropertyModel completeModel() {
      return GradlePropertyModelBuilder.create(myDslElement).buildResolved();
    }

    @Override
    @NotNull
    protected GradleDslElement getDslElement() {
      return myDslElement;
    }

    @Override
    void setDslElement(@NotNull GradleDslElement dslElement) {
      myDslElement = (GradleDslExpressionMap)dslElement;
    }
  }

  static final class CompactNotation extends ArtifactDependencyModelImpl {
    @NotNull private GradleDslSimpleExpression myDslExpression;

    @Nullable
    static CompactNotation create(@NotNull String configurationName,
                                  @NotNull GradleDslSimpleExpression dslExpression,
                                  @Nullable GradleDslClosure configurationElement,
                                  @NotNull Maintainer maintainer) {
      String value = dslExpression.getValue(String.class);
      if (value == null || value.trim().isEmpty()) {
        return null;
      }
      CompactNotation notation = new CompactNotation(configurationName, dslExpression, configurationElement, maintainer);
      // Check if the create notation is valid i.e it has a name
      return (notation.name().getValueType() != NONE) ? notation : null;
    }

    private CompactNotation(@NotNull String configurationName,
                            @NotNull GradleDslSimpleExpression dslExpression,
                            @Nullable GradleDslClosure configurationElement,
                            @NotNull Maintainer maintainer) {
      super(configurationElement, configurationName, maintainer);
      myDslExpression = dslExpression;
    }

    @NotNull
    public ResolvedPropertyModel createModelFor(@NotNull String name,
                                                @NotNull Function<ArtifactDependencySpec, String> getFunc,
                                                @NotNull BiConsumer<ArtifactDependencySpecImpl, String> setFunc,
                                                boolean canDelete) {
      GradleDslSimpleExpression element = mySetThrough ? resolveElement(myDslExpression) : myDslExpression;
      FakeElement fakeElement =
        new FakeArtifactElement(element.getParent(), GradleNameElement.fake(name), element, getFunc, setFunc, canDelete);
      return GradlePropertyModelBuilder.create(fakeElement).addTransform(new FakeElementTransform()).buildResolved();
    }

    @Override
    @NotNull
    public ResolvedPropertyModel name() {
      return createModelFor("name", ArtifactDependencySpec::getName, ArtifactDependencySpecImpl::setName, false);
    }

    @Override
    @NotNull
    public ResolvedPropertyModel group() {
      return createModelFor("group", ArtifactDependencySpec::getGroup, ArtifactDependencySpecImpl::setGroup, true);
    }

    @Override
    @NotNull
    public ResolvedPropertyModel version() {
      return createModelFor("version", ArtifactDependencySpec::getVersion, ArtifactDependencySpecImpl::setVersion, true);
    }

    @Override
    @NotNull
    public ResolvedPropertyModel classifier() {
      return createModelFor("classifier", ArtifactDependencySpec::getClassifier, ArtifactDependencySpecImpl::setClassifier, true);
    }

    @Override
    @NotNull
    public ResolvedPropertyModel extension() {
      return createModelFor("extension", ArtifactDependencySpec::getExtension, ArtifactDependencySpecImpl::setExtension, true);
    }

    @NotNull
    @Override
    public ResolvedPropertyModel completeModel() {
      return GradlePropertyModelBuilder.create(myDslExpression).buildResolved();
    }

    @Override
    @NotNull
    protected GradleDslElement getDslElement() {
      return myDslExpression;
    }

    @Override
    void setDslElement(@NotNull GradleDslElement dslElement) {
      // We do not expect changes to happen to dslElement while in setThrough mode. Make sure this is not used in an unexpected and
      // not tested way.
      assert !mySetThrough;
      myDslExpression = (GradleDslSimpleExpression)dslElement;
    }

    @Override
    @Nullable
    public PsiElement getPsiElement() {
      // The GradleDslElement#getPsiElement will not always be the correct literal. We correct this by getting the expression.
      return myDslExpression.getExpression();
    }
  }
}
