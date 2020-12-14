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
package com.android.tools.idea.gradle.dsl.parser;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * A parser for BUILD.gradle files. Used to build up a {@link GradleBuildModel} from the underlying file.
 *
 * Standard implementations of {@link GradleDslParser} should allow the setting of a {@link GradleDslFile} (e.g as a constructor argument),
 * when {@link #parse()} is called the parser should set the properties obtained onto the {@link GradleDslFile}.
 *
 * The {@link GradleDslParser} also contains several helper methods to work with the language specific subclasses of {@link PsiElement};
 * these are utilized by the {@link GradleBuildModel}.
 *
 * This interface aims to allow the {@link GradleBuildModel} to support different languages, each language should have its
 * own implementation of both {@link GradleDslParser} and {@link GradleDslWriter}.
 *
 * Note: The methods on this interface are marked with whether or not they require read access.
 * Read access can be obtained using {@link Application#runReadAction(Computable)}, among other ways.
 */
public interface GradleDslParser extends GradleDslNameConverter {
  /**
   * Instructs the parser perform its parsing operation. This method REQUIRES read access.
   */
  void parse();

  /**
   * Converts a given {@link Object} to the language specific {@link PsiElement}, this method is used to convert newly set or parsed values.
   * This method does REQUIRE read access.
   */
  @Nullable
  PsiElement convertToPsiElement(@NotNull GradleDslSimpleExpression context, @NotNull Object literal);

  /**
   * Sets up various properties of the GradleDslLiteral based on the new PsiElement to be set.
   */
  void setUpForNewValue(@NotNull GradleDslLiteral context, @Nullable PsiElement newValue);

  /**
   * Extracts a value {@link Object} from a given {@link PsiElement}. The {@code resolve} parameter determines
   * whether or not the returned value should contained resolved references to variables. e.g either "android-${version}" (unresolved)
   * or "android-23" (resolved). A {@link GradleDslSimpleExpression} is needed to resolve any variable names that need
   * to be injected.
   *
   * This method REQUIRES read access.
   */
  @Nullable
  Object extractValue(@NotNull GradleDslSimpleExpression context, @NotNull PsiElement literal, boolean resolve);

  /**
   * Builds an excludes block for a list of {@link ArtifactDependencySpec}s
   */
  @Nullable
  PsiElement convertToExcludesBlock(@NotNull List<ArtifactDependencySpec> excludes);

  /**
   * @param elementToCheck GradleDslElement, returns false if a non-string element is provided.
   * @return whether the string represented by this GradleDslElement should be interpolated.
   */
  boolean shouldInterpolate(@NotNull GradleDslElement elementToCheck);

  /**
   * Returns a list of {@link GradleReferenceInjection}s that were derived from {@code psiElement} .
   * A {@link GradleDslSimpleExpression} is needed to resolve any variable names that need to be injected.
   * This method only returns GradleReferenceInjections for which {@link GradleReferenceInjection#isResolved()}
   * returns true.
   *
   * This method REQUIRES read access.
   */
  @NotNull
  List<GradleReferenceInjection> getResolvedInjections(@NotNull GradleDslSimpleExpression context, @NotNull PsiElement psiElement);

  /**
   * Same as {@link #getResolvedInjections(GradleDslSimpleExpression, PsiElement)} apart from we also return references where
   * {@link GradleReferenceInjection#isResolved()} returns false.
   *
   * This method REQUIRES read access.
   */
  @NotNull
  List<GradleReferenceInjection> getInjections(@NotNull GradleDslSimpleExpression context, @NotNull PsiElement psiElement);

  /**
   * This method creates an empty block element (possibly nested) from {@code nameParts} with {@code parentElement} as a parent.
   * This method returns null if any of the block names are unrecognized and will return the parent element if {@code nameParts} is
   * empty.
   */
  @Nullable
  GradlePropertiesDslElement getBlockElement(@NotNull List<String> nameParts,
                                             @NotNull GradlePropertiesDslElement parentElement,
                                             @Nullable GradleNameElement nameElement);

  class Adapter implements GradleDslParser {
    @Override
    public void parse() { }

    @Override
    @Nullable
    public PsiElement convertToPsiElement(@NotNull GradleDslSimpleExpression context, @NotNull Object literal) {
      return null;
    }

    @Override
    public void setUpForNewValue(@NotNull GradleDslLiteral context, @Nullable PsiElement newValue) { }

    @Override
    @Nullable
    public Object extractValue(@NotNull GradleDslSimpleExpression context, @NotNull PsiElement literal, boolean resolve) {
      return null;
    }

    @Override
    @Nullable
    public PsiElement convertToExcludesBlock(@NotNull List<ArtifactDependencySpec> specs) { return null; }

    @Override
    public boolean shouldInterpolate(@NotNull GradleDslElement elementToCheck) { return false; }

    @Override
    @NotNull
    public List<GradleReferenceInjection> getResolvedInjections(@NotNull GradleDslSimpleExpression context, @NotNull PsiElement psiElement) {
      return Collections.emptyList();
    }

    @Override
    @NotNull
    public List<GradleReferenceInjection> getInjections(@NotNull GradleDslSimpleExpression context, @NotNull PsiElement psiElement) {
      return Collections.emptyList();
    }

    @Override
    @Nullable
    public GradlePropertiesDslElement getBlockElement(@NotNull List<String> nameParts,
                                                      @NotNull GradlePropertiesDslElement parentElement,
                                                      @Nullable GradleNameElement nameElement) {
      return null;
    }
  }
}
