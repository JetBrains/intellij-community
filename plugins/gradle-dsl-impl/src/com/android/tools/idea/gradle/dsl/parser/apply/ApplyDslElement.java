/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.apply;

import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ApplyDslElement extends GradlePropertiesDslElement {
  @NonNls public static final String APPLY_BLOCK_NAME = "apply";
  @NonNls private static final String FROM = "from";
  // The GradleDslFile that represents the virtual file that has been applied.
  // This will be set when parsing the build file we belong to.
  @NotNull private final List<GradleDslFile> myAppliedDslFiles = new ArrayList<>();

  public ApplyDslElement(@NotNull GradleDslElement parent) {
    super(parent, null, GradleNameElement.create(APPLY_BLOCK_NAME));
    parent.getDslFile().registerApplyElement(this);
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    GradleDslSimpleExpression from = extractFrom(element);
    if (from != null) {
      // Try and find the given file.
      String fileName = attemptToExtractFileName(from);
      if (fileName != null) {
        File realFile = new File(fileName);
        VirtualFile file;
        if (realFile.exists() && realFile.isAbsolute()) {
          file = LocalFileSystem.getInstance().findFileByIoFile(realFile);
        } else {
          VirtualFile parsingRoot = getDslFile().getContext().getCurrentParsingRoot();
          if (parsingRoot == null) {
            parsingRoot = getDslFile().getFile().getParent();
          } else {
              parsingRoot = parsingRoot.getParent();
          }
          file =
            VirtualFileManager.getInstance().findFileByUrl(parsingRoot + "/" + fileName);
        }
        if (file != null) {
          // Parse the file
          GradleDslFile dslFile = getDslFile().getContext().getOrCreateBuildFile(file, true);
          myAppliedDslFiles.add(dslFile);

          if (myParent instanceof GradlePropertiesDslElement) {
            ((GradlePropertiesDslElement)myParent).addAppliedModelProperties(dslFile);
          }
        }
      }
    }

    super.addParsedElement(element);
  }

  @Override
  @Nullable
  public PsiElement getPsiElement() {
    // This class is used to just group different kinds of apply statements, we make sure to return the parents PsiElement to ensure
    // elements can be positioned correctly.
    return myParent == null ? null : myParent.getPsiElement();
  }

  @Override
  @Nullable
  public PsiElement create() {
    return myParent == null ? null : myParent.create();
  }

  @Override
  public void setPsiElement(@Nullable PsiElement psiElement) {
  }

  @NotNull
  public List<GradleDslFile> getAppliedDslFiles() {
    return myAppliedDslFiles;
  }

  @Nullable
  private static String attemptToExtractFileName(@NotNull GradleDslSimpleExpression element) {
    return (element).getValue(String.class);
  }

  @Nullable
  private static GradleDslSimpleExpression extractFrom(@NotNull GradleDslElement element) {
    if (element instanceof GradleDslExpressionMap) {
      return ((GradleDslExpressionMap)element).getPropertyElement(FROM, GradleDslSimpleExpression.class);
    }
    if (element instanceof GradleDslMethodCall) {
      return ((GradleDslMethodCall) element).getArgumentsElement().getPropertyElement(FROM, GradleDslSimpleExpression.class);
    }
    return null;
  }
}
