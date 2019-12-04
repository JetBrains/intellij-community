// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.inspection.highlightTemplate;

import com.intellij.codeInspection.*;
import com.intellij.dupLocator.iterators.CountingNodeIterator;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.Matcher;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.StructuralSearchException;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.impl.matcher.iterators.SsrFilteringNodeIterator;
import com.intellij.structuralsearch.impl.matcher.predicates.ScriptSupport;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchFullInspection extends LocalInspectionTool {
  static final Object LOCK = new Object(); // hack to avoid race conditions in SSR

  private final List<Configuration> myConfigurations = ContainerUtil.createLockFreeCopyOnWriteList();
  private Map<Configuration, MatchContext> myCompiledOptionsCache = new HashMap<>();
  final Set<String> myProblemsReported = new HashSet<>(1);
  private final String myName;
  private final String myShortName;

  public StructuralSearchFullInspection(Configuration configuration) {
    myName = configuration.getName();
    myShortName = configuration.getUuid().toString();
    myConfigurations.add(configuration);
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getDisplayName() {
    return myName;
  }

  @NotNull
  @Override
  public String getShortName() {
    return myShortName;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String[] getGroupPath() {
    return new String[]{"General", "Structural Search"};
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    final PsiFile file = holder.getFile();
    if (!(file.getFileType() instanceof LanguageFileType)) return PsiElementVisitor.EMPTY_VISITOR;
    final Project project = holder.getManager().getProject();
    if (myConfigurations.isEmpty()) return PsiElementVisitor.EMPTY_VISITOR;
    final Matcher matcher = new Matcher(project);
    myCompiledOptionsCache = SSBasedInspectionCompiledPatternsCache.getCompiledOptions(myConfigurations, matcher, myCompiledOptionsCache);
    if (myCompiledOptionsCache.isEmpty()) return PsiElementVisitor.EMPTY_VISITOR;

    return new PsiElementVisitor() {
      final PairProcessor<MatchResult, Configuration> processor = (matchResult, configuration) -> {
        final PsiElement element = matchResult.getMatch();
        final String name = configuration.getName();
        final LocalQuickFix fix = SSBasedInspection.createQuickFix(project, matchResult, configuration);
        holder.registerProblem(
          holder.getManager().createProblemDescriptor(element, name, fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly)
        );
        return true;
      };

      @Override
      public void visitElement(PsiElement element) {
        if (LexicalNodesFilter.getInstance().accepts(element)) return;
        synchronized (LOCK) {
          final SsrFilteringNodeIterator matchedNodes = new SsrFilteringNodeIterator(element);
          for (Map.Entry<Configuration, MatchContext> entry : myCompiledOptionsCache.entrySet()) {
            final Configuration configuration = entry.getKey();
            final MatchContext context = entry.getValue();
            if (context == null) continue;

            if (Matcher.checkIfShouldAttemptToMatch(context, matchedNodes)) {
              final int nodeCount = context.getPattern().getNodeCount();
              try {
                matcher.processMatchesInElement(context, configuration, new CountingNodeIterator(nodeCount, matchedNodes), processor);
              }
              catch (StructuralSearchException e) {
                if (myProblemsReported.add(configuration.getName())) { // don't overwhelm the user with messages
                  final String message = e.getMessage().replace(ScriptSupport.UUID, "");
                  UIUtil.SSR_NOTIFICATION_GROUP.createNotification(NotificationType.ERROR)
                    .setContent(SSRBundle.message("inspection.script.problem", message, configuration.getName()))
                    .setImportant(true)
                    .notify(element.getProject());
                }
              }
              matchedNodes.reset();
            }
          }
        }
      }
    };
  }
}
