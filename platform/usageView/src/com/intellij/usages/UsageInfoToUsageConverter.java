/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.usages;

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 17, 2005
 */
public class UsageInfoToUsageConverter {
  private UsageInfoToUsageConverter() {
  }

  public static class TargetElementsDescriptor {
    private final List<SmartPsiElementPointer<PsiElement>> myPrimarySearchedElements;
    private final List<SmartPsiElementPointer<PsiElement>> myAdditionalSearchedElements;

    public TargetElementsDescriptor(@NotNull PsiElement element) {
      this(new PsiElement[]{element});
    }

    public TargetElementsDescriptor(@NotNull PsiElement[] primarySearchedElements) {
      this(primarySearchedElements, PsiElement.EMPTY_ARRAY);
    }

    public TargetElementsDescriptor(@NotNull PsiElement[] primarySearchedElements, @NotNull PsiElement[] additionalSearchedElements) {
      myPrimarySearchedElements = convertToSmartPointers(primarySearchedElements);
      myAdditionalSearchedElements = convertToSmartPointers(additionalSearchedElements);
    }

    private static final Function<SmartPsiElementPointer<PsiElement>,PsiElement> SMARTPOINTER_TO_ELEMENT_MAPPER = new Function<SmartPsiElementPointer<PsiElement>, PsiElement>() {
      @Override
      public PsiElement fun(final SmartPsiElementPointer<PsiElement> pointer) {
        return pointer.getElement();
      }
    };

    @NotNull
    private static PsiElement[] convertToPsiElements(@NotNull List<SmartPsiElementPointer<PsiElement>> primary) {
      return ContainerUtil.toArray(ContainerUtil.mapNotNull(primary, SMARTPOINTER_TO_ELEMENT_MAPPER), PsiElement.ARRAY_FACTORY);
    }

    @NotNull
    private static List<SmartPsiElementPointer<PsiElement>> convertToSmartPointers(@NotNull PsiElement[] primaryElements) {
      if (primaryElements.length == 0) return Collections.emptyList();
  
      final SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(primaryElements[0].getProject());
      return ContainerUtil.mapNotNull(primaryElements, new Function<PsiElement, SmartPsiElementPointer<PsiElement>>() {
              @Override
              public SmartPsiElementPointer<PsiElement> fun(final PsiElement s) {
                return smartPointerManager.createSmartPsiElementPointer(s);
              }
            });
    }

    /**
     * A read-only attribute describing the target as a "primary" target.
     * A primary target is a target that was the main purpose of the search.
     * All usages of a non-primary target should be considered as a special case of usages of the corresponding primary target.
     * Example: searching field and its getter and setter methods -
     *          the field searched is a primary target, and its accessor methods are non-primary targets, because
     *          for this particular search usages of getter/setter methods are to be considered as a usages of the corresponding field.
     */
    @NotNull
    public PsiElement[] getPrimaryElements() {
      return convertToPsiElements(myPrimarySearchedElements);
    }

    @NotNull
    public PsiElement[] getAdditionalElements() {
      return convertToPsiElements(myAdditionalSearchedElements);
    }

    @NotNull
    public List<PsiElement> getAllElements() {
      List<PsiElement> result = new ArrayList<PsiElement>(myPrimarySearchedElements.size() + myAdditionalSearchedElements.size());
      for (SmartPsiElementPointer pointer : myPrimarySearchedElements) {
        PsiElement element = pointer.getElement();
        if (element != null) {
          result.add(element);
        }
      }
      for (SmartPsiElementPointer pointer : myAdditionalSearchedElements) {
        PsiElement element = pointer.getElement();
        if (element != null) {
          result.add(element);
        }
      }
      return result;
    }

    @NotNull
    public List<SmartPsiElementPointer<PsiElement>> getAllElementPointers() {
      List<SmartPsiElementPointer<PsiElement>> result = new ArrayList<SmartPsiElementPointer<PsiElement>>(myPrimarySearchedElements.size() + myAdditionalSearchedElements.size());
      result.addAll(myPrimarySearchedElements);
      result.addAll(myAdditionalSearchedElements);
      return result;
    }
  }

  @NotNull
  public static Usage convert(@NotNull TargetElementsDescriptor descriptor, @NotNull UsageInfo usageInfo) {
    PsiElement[] primaryElements = descriptor.getPrimaryElements();

    return convert(primaryElements, usageInfo);
  }

  @NotNull
  public static Usage convert(@NotNull PsiElement[] primaryElements, @NotNull UsageInfo usageInfo) {
    PsiElement usageElement = usageInfo.getElement();
    for(ReadWriteAccessDetector detector: Extensions.getExtensions(ReadWriteAccessDetector.EP_NAME)) {
      if (isReadWriteAccessibleElements(primaryElements, detector)) {
        final ReadWriteAccessDetector.Access rwAccess = detector.getExpressionAccess(usageElement);
        return new ReadWriteAccessUsageInfo2UsageAdapter(usageInfo,
                                                         rwAccess != ReadWriteAccessDetector.Access.Write,
                                                         rwAccess != ReadWriteAccessDetector.Access.Read);
      }
    }
    return new UsageInfo2UsageAdapter(usageInfo);
  }

  @NotNull
  public static Usage[] convert(@NotNull TargetElementsDescriptor descriptor, @NotNull UsageInfo[] usageInfos) {
    Usage[] usages = new Usage[usageInfos.length];
    for (int i = 0; i < usages.length; i++) {
      usages[i] = convert(descriptor, usageInfos[i]);
    }
    return usages;
  }

  @NotNull
  public static Usage[] convert(@NotNull final PsiElement[] primaryElements, @NotNull UsageInfo[] usageInfos) {
    Usage[] usages = ContainerUtil.map(usageInfos, new Function<UsageInfo, Usage>() {
      @Override
      public Usage fun(UsageInfo info) {
        return convert(primaryElements, info);
      }
    }, new Usage[usageInfos.length]);
    return usages;
  }

  private static boolean isReadWriteAccessibleElements(@NotNull PsiElement[] elements, @NotNull ReadWriteAccessDetector detector) {
    if (elements.length == 0) {
      return false;
    }
    for (PsiElement element : elements) {
      if (!detector.isReadWriteAccessible(element)) return false;
    }
    return true;
  }
}
