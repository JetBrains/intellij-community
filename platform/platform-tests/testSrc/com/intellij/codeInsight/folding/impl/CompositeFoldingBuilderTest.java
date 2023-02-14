/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.folding.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.CustomFoldingBuilder;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class CompositeFoldingBuilderTest extends AbstractEditorTest {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    init("Some plain text to fold", PlainTextFileType.INSTANCE);
  }

  public void testAllowOnlyOneDescriptorPerTextRange() {
    final FoldingBuilder first = createDummyFoldingBuilder("plain", "mountain", false);
    final FoldingBuilder second = createDummyFoldingBuilder("plain", "tree", false);

    LanguageFolding.INSTANCE.addExplicitExtension(PlainTextLanguage.INSTANCE, first);
    LanguageFolding.INSTANCE.addExplicitExtension(PlainTextLanguage.INSTANCE, second);

    try {
      PsiFile file = getFile();
      List<FoldingUpdate.RegionInfo> regionInfos = FoldingUpdate.getFoldingsFor(file, false);
      int regionCount = ContainerUtil.count(regionInfos, i -> file.equals(i.element));

      assert regionCount == 1: "Only one descriptor allowed for the same text range. Descriptors: " + regionInfos;
    }
    finally {
      LanguageFolding.INSTANCE.removeExplicitExtension(PlainTextLanguage.INSTANCE, first);
      LanguageFolding.INSTANCE.removeExplicitExtension(PlainTextLanguage.INSTANCE, second);
    }
  }

  public void testOverrideGetText() {
    final FoldingBuilder first = createDummyFoldingBuilder("plain", "mountain", true);

    LanguageFolding.INSTANCE.addExplicitExtension(PlainTextLanguage.INSTANCE, first);

    try {
      PsiFile file = getFile();
      List<FoldingUpdate.RegionInfo> regionInfos = FoldingUpdate.getFoldingsFor(file, false);
      int regionCount = ContainerUtil.count(regionInfos, i -> file.equals(i.element));
      assertEquals("mountain", regionInfos.get(0).descriptor.getPlaceholderText());

      assert regionCount == 1: "Only one descriptor allowed for the same text range. Descriptors: " + regionInfos;
    }
    finally {
      LanguageFolding.INSTANCE.removeExplicitExtension(PlainTextLanguage.INSTANCE, first);
    }
  }

  @NotNull
  private static FoldingBuilder createDummyFoldingBuilder(final String textToFold,
                                                          final String placeholderText,
                                                          final boolean overrideGetText) {
    return new CustomFoldingBuilder() {
      @Override
      protected void buildLanguageFoldRegions(
        @NotNull List<FoldingDescriptor> descriptors,
        @NotNull PsiElement root,
        @NotNull Document document,
        boolean quick)
      {
        final int index = root.getText().indexOf(textToFold);
        TextRange textRange = new TextRange(index, index + textToFold.length());
        if (overrideGetText) {
          descriptors.add(new FoldingDescriptor(root.getNode(), textRange) {
            @Nullable
            @Override
            public String getPlaceholderText() {
              return placeholderText;
            }
          });
        }
        else {
          descriptors.add(new FoldingDescriptor(root.getNode(), textRange, null, placeholderText, true, Collections.emptySet()));
        }
      }

      @Override
      protected String getLanguagePlaceholderText(@NotNull ASTNode node, @NotNull TextRange range) {
        return "";
      }

      @Override
      protected boolean isRegionCollapsedByDefault(@NotNull ASTNode node) {
        return true;
      }
    };
  }
}
