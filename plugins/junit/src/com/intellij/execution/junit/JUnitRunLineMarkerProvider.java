/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.junit;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.lineMarker.RunLineMarkerInfo;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class JUnitRunLineMarkerProvider implements LineMarkerProvider {

  private static final Function<PsiElement, String> TOOLTIP_PROVIDER = new Function<PsiElement, String>() {
    @Override
    public String fun(PsiElement element) {
      return "Run Test";
    }
  };

  @Nullable
  @Override
  public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element) {
    if (element instanceof PsiClass && JUnitUtil.isTestClass((PsiClass)element, false, true))
      return new RunLineMarkerInfo(element, JUnitConfigurationType.getInstance().getIcon(), TOOLTIP_PROVIDER);
    if (element instanceof PsiMethod && JUnitUtil.isTestMethod(new PsiLocation<PsiMethod>((PsiMethod)element)))
      return new RunLineMarkerInfo(element, JUnitConfigurationType.getInstance().getIcon(), TOOLTIP_PROVIDER);
    return null;

  }

  @Override
  public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {

  }
}
