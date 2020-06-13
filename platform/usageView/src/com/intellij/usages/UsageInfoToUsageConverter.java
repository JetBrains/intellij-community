// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.codeInsight.highlighting.ReadWriteUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public final class UsageInfoToUsageConverter {
  private UsageInfoToUsageConverter() {
  }

  public static class TargetElementsDescriptor {
    private final List<SmartPsiElementPointer<PsiElement>> myPrimarySearchedElements;
    private final List<SmartPsiElementPointer<PsiElement>> myAdditionalSearchedElements;

    public TargetElementsDescriptor(@NotNull PsiElement element) {
      this(new PsiElement[]{element});
    }

    public TargetElementsDescriptor(@NotNull PsiElement @NotNull [] primarySearchedElements) {
      this(primarySearchedElements, PsiElement.EMPTY_ARRAY);
    }

    public TargetElementsDescriptor(@NotNull PsiElement @NotNull [] primarySearchedElements,
                                    @NotNull PsiElement @NotNull [] additionalSearchedElements) {
      myPrimarySearchedElements = convertToSmartPointers(primarySearchedElements);
      myAdditionalSearchedElements = convertToSmartPointers(additionalSearchedElements);
    }

    private static @NotNull PsiElement @NotNull [] convertToPsiElements(@NotNull List<? extends SmartPsiElementPointer<PsiElement>> primary) {
      return ContainerUtil.toArray(ContainerUtil.mapNotNull(primary, SmartPsiElementPointer::getElement), PsiElement.ARRAY_FACTORY);
    }

    @NotNull
    private static List<@NotNull SmartPsiElementPointer<PsiElement>> convertToSmartPointers(@NotNull PsiElement @NotNull [] primaryElements) {
      if (primaryElements.length == 0) return Collections.emptyList();
      final SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(primaryElements[0].getProject());
      return ContainerUtil.mapNotNull(primaryElements, smartPointerManager::createSmartPsiElementPointer);
    }

    /**
     * A read-only attribute describing the target as a "primary" target.
     * A primary target is a target that was the main purpose of the search.
     * All usages of a non-primary target should be considered as a special case of usages of the corresponding primary target.
     * Example: searching field and its getter and setter methods -
     * the field searched is a primary target, and its accessor methods are non-primary targets, because
     * for this particular search usages of getter/setter methods are to be considered as a usages of the corresponding field.
     */
    public @NotNull PsiElement @NotNull [] getPrimaryElements() {
      return convertToPsiElements(myPrimarySearchedElements);
    }

    public @NotNull PsiElement @NotNull [] getAdditionalElements() {
      return convertToPsiElements(myAdditionalSearchedElements);
    }

    @NotNull
    public List<PsiElement> getAllElements() {
      List<PsiElement> result = new ArrayList<>(myPrimarySearchedElements.size() + myAdditionalSearchedElements.size());
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
      return ContainerUtil.concat(myPrimarySearchedElements, myAdditionalSearchedElements);
    }
  }

  @NotNull
  public static Usage convert(@NotNull TargetElementsDescriptor descriptor, @NotNull UsageInfo usageInfo) {
    PsiElement[] primaryElements = descriptor.getPrimaryElements();

    return convert(primaryElements, usageInfo);
  }

  @NotNull
  public static Usage convert(PsiElement @NotNull [] primaryElements, @NotNull UsageInfo usageInfo) {
    PsiElement usageElement = usageInfo.getElement();
    if (usageElement != null && primaryElements.length != 0) {
      ReadWriteAccessDetector.Access rwAccess = ReadWriteUtil.getReadWriteAccess(primaryElements, usageElement);
      if (rwAccess != null) {
        return new ReadWriteAccessUsageInfo2UsageAdapter(usageInfo,
                                                         rwAccess != ReadWriteAccessDetector.Access.Write,
                                                         rwAccess != ReadWriteAccessDetector.Access.Read);
      }
    }
    return new UsageInfo2UsageAdapter(usageInfo);
  }

  public static Usage @NotNull [] convert(@NotNull TargetElementsDescriptor descriptor, UsageInfo @NotNull [] usageInfos) {
    Usage[] usages = new Usage[usageInfos.length];
    for (int i = 0; i < usages.length; i++) {
      usages[i] = convert(descriptor, usageInfos[i]);
    }
    return usages;
  }


  public static Usage @NotNull [] convert(final PsiElement @NotNull [] primaryElements, UsageInfo @NotNull [] usageInfos) {
    return ContainerUtil.map(usageInfos, info -> convert(primaryElements, info), new Usage[usageInfos.length]);
  }
}
