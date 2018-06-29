// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.inspection.highlightTemplate;

import com.intellij.codeInspection.*;
import com.intellij.dupLocator.iterators.CountingNodeIterator;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.impl.matcher.iterators.SsrFilteringNodeIterator;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.ConfigurationManager;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
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
  static final Object LOCK = new Object(); // hack to avoid race conditions in SSR

  static final String SHORT_NAME = "SSBasedInspection";
  private final List<Configuration> myConfigurations = new ArrayList<>();
  final Set<String> myProblemsReported = new HashSet<>(1);

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    ConfigurationManager.writeConfigurations(node, myConfigurations);
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    myProblemsReported.clear();
    myConfigurations.clear();
    ConfigurationManager.readConfigurations(node, myConfigurations);
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
                  //noinspection InstanceofCatchParameter
                  UIUtil.SSR_NOTIFICATION_GROUP.createNotification(NotificationType.ERROR)
                                               .setContent(e instanceof StructuralSearchScriptException
                                                           ? SSRBundle.message("inspection.script.problem", e.getCause(), configuration.getName())
                                                           : SSRBundle.message("inspection.template.problem", e.getMessage()))
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

  static LocalQuickFix createQuickFix(final Project project, final MatchResult matchResult, final Configuration configuration) {
    if (!(configuration instanceof ReplaceConfiguration)) return null;
    ReplaceConfiguration replaceConfiguration = (ReplaceConfiguration)configuration;
    final Replacer replacer = new Replacer(project, replaceConfiguration.getReplaceOptions());
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
        if (element != null) {
          replacer.replace(replacementInfo);
        }
      }

      @Override
      @NotNull
      public String getFamilyName() {
        //noinspection DialogTitleCapitalization
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
