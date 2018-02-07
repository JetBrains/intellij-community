/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.structureView.customRegions;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.folding.*;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Rustam Vishnyakov
 */
public class CustomRegionStructureUtil {

  public static Collection<StructureViewTreeElement> groupByCustomRegions(@NotNull PsiElement rootElement,
                                                                          @NotNull Collection<StructureViewTreeElement> originalElements) {
    if (rootElement instanceof StubBasedPsiElement &&
        ((StubBasedPsiElement)rootElement).getStub() != null) {
      return originalElements;
    }
    Set<TextRange> childrenRanges = ContainerUtil.map2SetNotNull(originalElements, element -> {
      Object value = element.getValue();
      return value instanceof PsiElement ? getTextRange((PsiElement)value) : null;
    });
    Collection<CustomRegionTreeElement> customRegions = collectCustomRegions(rootElement, childrenRanges);
    if (customRegions.size() > 0) {
      List<StructureViewTreeElement> result = new ArrayList<>(customRegions);
      for (StructureViewTreeElement element : originalElements) {
        ProgressManager.checkCanceled();
        boolean isInCustomRegion = false;
        for (CustomRegionTreeElement customRegion : customRegions) {
          if (customRegion.containsElement(element)) {
            customRegion.addChild(element);
            isInCustomRegion = true;
            break;
          }
        }
        if (!isInCustomRegion) result.add(element);
      }
      return result;
    }
    return originalElements;
  }

  /*
   * Fix cases when a line comment before an element (for example, method) gets inside it as a first child.
   */
  private static TextRange getTextRange(@NotNull PsiElement element) {
    PsiElement first = element.getFirstChild();
    if (first instanceof PsiComment && !first.textContains('\n')) {
      PsiElement next = first.getNextSibling();
      if (next instanceof PsiWhiteSpace) next = next.getNextSibling();
      if (next != null) {
        return new TextRange(next.getTextRange().getStartOffset(), element.getTextRange().getEndOffset());
      }
    }
    return element.getTextRange();
  }

  private static Collection<CustomRegionTreeElement> collectCustomRegions(@NotNull PsiElement rootElement, @NotNull Set<TextRange> ranges) {
    TextRange rootRange = getTextRange(rootElement);
    Iterator<PsiElement> iterator = SyntaxTraverser.psiTraverser(rootElement)
      .filter(element -> isCustomRegionCommentCandidate(element) &&
                         rootRange.contains(element.getTextRange()) &&
                         !isInsideRanges(element, ranges))
      .iterator();

    List<CustomRegionTreeElement> customRegions = ContainerUtil.newSmartList();
    CustomRegionTreeElement currRegionElement = null;
    CustomFoldingProvider provider = null;
    while (iterator.hasNext()) {
      ProgressManager.checkCanceled();
      PsiElement child = iterator.next();
      if (provider == null) provider = getProvider(child);
      if (provider != null) {
        String commentText = child.getText();
        if (provider.isCustomRegionStart(commentText)) {
          if (currRegionElement == null) {
            currRegionElement = new CustomRegionTreeElement(child, provider);
            customRegions.add(currRegionElement);
          }
          else {
            currRegionElement = currRegionElement.createNestedRegion(child);
          }
        }
        else if (provider.isCustomRegionEnd(commentText) && currRegionElement != null) {
          currRegionElement = currRegionElement.endRegion(child);
        }
      }
    }
    return customRegions;
  }

  @Nullable
  static CustomFoldingProvider getProvider(@NotNull PsiElement element) {
    ASTNode node = element.getNode();
    if (node != null) {
      for (CustomFoldingProvider provider : CustomFoldingProvider.getAllProviders()) {
        if (provider.isCustomRegionStart(node.getText())) {
          return provider;
        }
      }
    }
    return null;
  }

  private static boolean isInsideRanges(@NotNull PsiElement element, @NotNull Set<TextRange> ranges) {
    for (TextRange range : ranges) {
      TextRange elementRange = element.getTextRange();
      if (range.contains(elementRange.getStartOffset()) || range.contains(elementRange.getEndOffset())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isCustomRegionCommentCandidate(@NotNull PsiElement element) {
    Language language = element.getLanguage();
    if (!Language.ANY.is(language)) {
      final FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(language);
      if (foldingBuilder instanceof CustomFoldingBuilder) {
        return ((CustomFoldingBuilder)foldingBuilder).isCustomFoldingCandidate(element);
      }
      else if (foldingBuilder instanceof CompositeFoldingBuilder) {
        for (FoldingBuilder simpleBuilder : ((CompositeFoldingBuilder)foldingBuilder).getAllBuilders()) {
          if (simpleBuilder instanceof CustomFoldingBuilder) {
            if (((CustomFoldingBuilder)simpleBuilder).isCustomFoldingCandidate(element)) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }
}
