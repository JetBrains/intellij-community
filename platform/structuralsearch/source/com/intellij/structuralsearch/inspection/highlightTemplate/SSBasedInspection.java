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
package com.intellij.structuralsearch.inspection.highlightTemplate;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.*;
import com.intellij.dupLocator.iterators.CountingNodeIterator;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.Matcher;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.StructuralSearchException;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.MatcherImpl;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.impl.matcher.iterators.SsrFilteringNodeIterator;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.ConfigurationManager;
import com.intellij.util.PairProcessor;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.*;

/**
 * @author cdr
 */
public class SSBasedInspection extends LocalInspectionTool {
  private static final Object LOCK = new Object(); // hack to avoid race conditions in SSR

  static final String SHORT_NAME = "SSBasedInspection";
  private final List<Configuration> myConfigurations = new ArrayList<>();
  private final Set<String> myProblemsReported = new HashSet<>(1);

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    ConfigurationManager.writeConfigurations(node, myConfigurations, Collections.<Configuration>emptyList());
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    myProblemsReported.clear();
    myConfigurations.clear();
    ConfigurationManager.readConfigurations(node, myConfigurations, new ArrayList<>());
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GENERAL_GROUP_NAME;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return SSRBundle.message("SSRInspection.display.name");
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return SHORT_NAME;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    final Map<Configuration, MatchContext> compiledOptions =
      SSBasedInspectionCompiledPatternsCache.getCompiledOptions(myConfigurations, holder.getProject());

    if (compiledOptions.isEmpty()) return super.buildVisitor(holder, isOnTheFly);

    return new PsiElementVisitor() {
      final Matcher matcher = new Matcher(holder.getManager().getProject());
      final PairProcessor<MatchResult, Configuration> processor = (matchResult, configuration) -> {
        PsiElement element = matchResult.getMatch();
        String name = configuration.getName();
        LocalQuickFix fix = createQuickFix(holder.getManager().getProject(), matchResult, configuration);
        holder.registerProblem(
          holder.getManager().createProblemDescriptor(element, name, fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly)
        );
        return true;
      };

      @Override
      public void visitElement(PsiElement element) {
        synchronized (LOCK) {
          if (LexicalNodesFilter.getInstance().accepts(element)) return;
          final SsrFilteringNodeIterator matchedNodes = new SsrFilteringNodeIterator(element);
          for (Map.Entry<Configuration, MatchContext> entry : compiledOptions.entrySet()) {
            Configuration configuration = entry.getKey();
            MatchContext context = entry.getValue();

            if (MatcherImpl.checkIfShouldAttemptToMatch(context, matchedNodes)) {
              final int nodeCount = context.getPattern().getNodeCount();
              try {
                matcher.processMatchesInElement(context, configuration, new CountingNodeIterator(nodeCount, matchedNodes), processor);
              }
              catch (StructuralSearchException e) {
                if (myProblemsReported.add(configuration.getName())) { // don't overwhelm the user with messages
                  Notifications.Bus.notify(new Notification(SSRBundle.message("structural.search.title"),
                                                            SSRBundle.message("template.problem", configuration.getName()),
                                                            e.getMessage(),
                                                            NotificationType.ERROR), element.getProject());
                }
              }
              matchedNodes.reset();
            }
          }
        }
      }
    };
  }

  private static LocalQuickFix createQuickFix(final Project project, final MatchResult matchResult, final Configuration configuration) {
    if (!(configuration instanceof ReplaceConfiguration)) return null;
    ReplaceConfiguration replaceConfiguration = (ReplaceConfiguration)configuration;
    final Replacer replacer = new Replacer(project, replaceConfiguration.getOptions());
    final ReplacementInfo replacementInfo = replacer.buildReplacement(matchResult);

    return new LocalQuickFix() {
      @Override
      @NotNull
      public String getName() {
        return SSRBundle.message("SSRInspection.replace.with", replacementInfo.getReplacement());
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element != null && FileModificationService.getInstance().preparePsiElementsForWrite(element)) {
          replacer.replace(replacementInfo);
        }
      }

      @Override
      @NotNull
      public String getFamilyName() {
        return SSRBundle.message("SSRInspection.family.name");
      }
    };
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SSBasedInspectionOptions(myConfigurations).getComponent();
  }

  @TestOnly
  public void setConfigurations(@NotNull final List<Configuration> configurations, @NotNull final Project project) {
    myConfigurations.clear();
    myConfigurations.addAll(configurations);
  }
}
