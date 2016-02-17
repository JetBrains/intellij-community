
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
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl;
import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 */
public class HighlightVisitorInternalInspection extends BaseJavaBatchLocalInspectionTool {
  public static final Logger LOG = Logger.getInstance("#" + HighlightVisitorInternalInspection.class.getName());

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.BUGS_GROUP_NAME;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Highlight Visitor Internal";
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  @NotNull
  @Override
  public String getShortName() {
    return "HighlightVisitorInternal";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    final PsiFile file = holder.getFile();
    if (InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(file);
    if (virtualFile == null ||
        virtualFile.getFileType() != StdFileTypes.JAVA ||
        CompilerConfiguration.getInstance(holder.getProject()).isExcludedFromCompilation(virtualFile)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new HighlightVisitorImpl(JavaPsiFacade.getInstance(holder.getProject()).getResolveHelper()) {
      {
        prepareToRunAsInspection(new HighlightInfoHolder(file) {
          @Override
          public boolean add(@Nullable HighlightInfo info) {
            if (super.add(info)) {
              if (info != null && info.getSeverity() == HighlightSeverity.ERROR) {
                final int startOffset = info.getStartOffset();
                final PsiElement element = file.findElementAt(startOffset);
                if (element != null) {
                  holder.registerProblem(element, info.getDescription());
                }
              }
              return true;
            }
            return false;
          }

          @Override
          public boolean hasErrorResults() {
            //accept multiple errors per file
            return false;
          }
        });
      }
    };
  }
}
