/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
    private final List<SmartPsiElementPointer> myPrimarySearchedElements;
    private final List<SmartPsiElementPointer> myAdditionalSearchedElements;

    public TargetElementsDescriptor(PsiElement element) {
      this(new PsiElement[]{element});
    }

    public TargetElementsDescriptor(PsiElement[] primarySearchedElements) {
      this(primarySearchedElements, PsiElement.EMPTY_ARRAY);
    }

    public TargetElementsDescriptor(PsiElement[] primarySearchedElements, PsiElement[] additionalSearchedElements) {
      myPrimarySearchedElements = convertToSmartPointers(primarySearchedElements);
      myAdditionalSearchedElements = convertToSmartPointers(additionalSearchedElements);
    }

    private static final Function<SmartPsiElementPointer,PsiElement> SMARTPOINTER_TO_ELEMENT_MAPPER = new Function<SmartPsiElementPointer, PsiElement>() {
      @Override
      public PsiElement fun(final SmartPsiElementPointer s) {
        return s.getElement();
      }
    };
    private static PsiElement[] convertToPsiElements(final List<SmartPsiElementPointer> primary) {
      return ContainerUtil.map2Array(primary, PsiElement.class, SMARTPOINTER_TO_ELEMENT_MAPPER);
    }

    private static List<SmartPsiElementPointer> convertToSmartPointers(final PsiElement[] primaryElements) {
      return primaryElements != null ? ContainerUtil.mapNotNull(primaryElements, new Function<PsiElement, SmartPsiElementPointer>() {
        @Override
        public SmartPsiElementPointer fun(final PsiElement s) {
          return SmartPointerManager.getInstance(s.getProject()).createSmartPsiElementPointer(s);
        }
      }) : Collections.<SmartPsiElementPointer>emptyList();
    }

    /**
     * A read-only attribute describing the target as a "primary" target.
     * A primary target is a target that was the main purpose of the search.
     * All usages of a non-primary target should be considered as a special case of usages of the corresponding primary target.
     * Example: searching field and its getter and setter methods -
     *          the field searched is a primary target, and its accessor methods are non-primary targets, because
     *          for this particular search usages of getter/setter methods are to be considered as a usages of the corresponding field.
     */
    public PsiElement[] getPrimaryElements() {
      return convertToPsiElements(myPrimarySearchedElements);
    }

    public PsiElement[] getAdditionalElements() {
      return convertToPsiElements(myAdditionalSearchedElements);
    }
    public List<? extends PsiElement> getAllElements() {
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

  }

  @NotNull
  public static Usage convert(TargetElementsDescriptor descriptor, UsageInfo usageInfo) {
    final PsiElement[] primaryElements = descriptor.getPrimaryElements();

    for(ReadWriteAccessDetector detector: Extensions.getExtensions(ReadWriteAccessDetector.EP_NAME)) {
      if (isReadWriteAccessibleElements(primaryElements, detector)) {
        final PsiElement usageElement = usageInfo.getElement();
        final ReadWriteAccessDetector.Access rwAccess = detector.getExpressionAccess(usageElement);
        return new ReadWriteAccessUsageInfo2UsageAdapter(usageInfo,
                                                         rwAccess != ReadWriteAccessDetector.Access.Write,
                                                         rwAccess != ReadWriteAccessDetector.Access.Read);
      }
    }
    return new UsageInfo2UsageAdapter(usageInfo);
  }

  public static Usage[] convert(TargetElementsDescriptor descriptor, UsageInfo[] usageInfos) {
    Usage[] usages = new Usage[usageInfos.length];
    for (int i = 0; i < usages.length; i++) {
      usages[i] = convert(descriptor, usageInfos[i]);
    }
    return usages;
  }

  private static boolean isReadWriteAccessibleElements(final PsiElement[] elements, final ReadWriteAccessDetector detector) {
    if (elements.length == 0) {
      return false;
    }
    for (PsiElement element : elements) {
      if (!detector.isReadWriteAccessible(element)) return false;
    }
    return true;
  }
}
