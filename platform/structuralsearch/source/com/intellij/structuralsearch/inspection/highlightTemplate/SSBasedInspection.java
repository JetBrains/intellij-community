// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.inspection.highlightTemplate;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.dupLocator.iterators.CountingNodeIterator;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.Matcher;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.StructuralSearchException;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.impl.matcher.iterators.SsrFilteringNodeIterator;
import com.intellij.structuralsearch.impl.matcher.predicates.ScriptSupport;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.ConfigurationManager;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.ContainerUtil;
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

  public static final String SHORT_NAME = "SSBasedInspection";
  private final List<Configuration> myConfigurations = ContainerUtil.createLockFreeCopyOnWriteList();
  final Set<String> myProblemsReported = new HashSet<>(1);
  private InspectionProfileImpl mySessionProfile = null;

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
    return getGENERAL_GROUP_NAME();
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return SHORT_NAME;
  }

  public void setSessionProfile(InspectionProfileImpl profile) {
    mySessionProfile = profile;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    final Project project = holder.getManager().getProject();
    if (myConfigurations.isEmpty()) return PsiElementVisitor.EMPTY_VISITOR;

    final List<Configuration> configurations;
    final InspectionProfileImpl profile;
    if (Registry.is("ssr.separate.inspections")) {
      profile = (mySessionProfile != null) ? mySessionProfile : InspectionProfileManager.getInstance(project).getCurrentProfile();
      configurations = ContainerUtil.filter(myConfigurations, x -> profile.isToolEnabled(HighlightDisplayKey.find(x.getUuid().toString())));
      if (configurations.isEmpty()) return PsiElementVisitor.EMPTY_VISITOR;
    }
    else {
      profile = null;
      configurations = myConfigurations;
    }

    final Matcher matcher = new Matcher(project);
    final Map<Configuration, MatchContext> compiledOptions =
      SSBasedInspectionCompiledPatternsCache.getCompiledOptions(configurations, matcher);
    if (compiledOptions.isEmpty()) return PsiElementVisitor.EMPTY_VISITOR;

    return new PsiElementVisitor() {
      final PairProcessor<MatchResult, Configuration> processor = (matchResult, configuration) -> {
        final PsiElement element = matchResult.getMatch();
        final String name = configuration.getName();
        final LocalQuickFix fix = createQuickFix(project, matchResult, configuration);
        final ProblemDescriptor descriptor =
          holder.getManager().createProblemDescriptor(element, name, fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly);
        if (Registry.is("ssr.separate.inspections")) {
          descriptor.setFakeInspectionShortName(configuration.getUuid().toString());
        }
        holder.registerProblem(descriptor);
        return true;
      };

      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (LexicalNodesFilter.getInstance().accepts(element)) return;
        synchronized (LOCK) {
          final SsrFilteringNodeIterator matchedNodes = new SsrFilteringNodeIterator(element);
          for (Map.Entry<Configuration, MatchContext> entry : compiledOptions.entrySet()) {
            final Configuration configuration = entry.getKey();
            final MatchContext context = entry.getValue();
            if (context == null) continue;

            if (Matcher.checkIfShouldAttemptToMatch(context, matchedNodes) &&
                (profile == null || profile.isToolEnabled(HighlightDisplayKey.find(configuration.getUuid().toString()), element))) {
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

  static LocalQuickFix createQuickFix(final Project project, final MatchResult matchResult, final Configuration configuration) {
    if (!(configuration instanceof ReplaceConfiguration)) return null;
    final ReplaceConfiguration replaceConfiguration = (ReplaceConfiguration)configuration;
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
        final PsiElement element = descriptor.getPsiElement();
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
  public void setConfigurations(@NotNull final List<? extends Configuration> configurations, @NotNull final Project project) {
    myConfigurations.clear();
    myConfigurations.addAll(configurations);
  }

  public List<Configuration> getConfigurations() {
    return Collections.unmodifiableList(myConfigurations);
  }
}
