// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.inspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.ProblemDescriptorWithReporterName;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.DynamicGroupTool;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.dupLocator.iterators.CountingNodeIterator;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.impl.matcher.iterators.SsrFilteringNodeIterator;
import com.intellij.structuralsearch.impl.matcher.predicates.ScriptSupport;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.ConfigurationManager;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import com.intellij.structuralsearch.plugin.util.DuplicateFilteringResultSink;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.stream.Collectors;

public class SSBasedInspection extends LocalInspectionTool implements DynamicGroupTool {
  static final Object LOCK = new Object(); // hack to avoid race conditions in SSR

  @NonNls public static final String SHORT_NAME = "SSBasedInspection";
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
    return getGeneralGroupName();
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return SHORT_NAME;
  }

  @Override
  public void initialize(@NotNull GlobalInspectionContext context) {
    super.initialize(context);
    mySessionProfile = ((GlobalInspectionContextBase)context).getCurrentProfile();
  }

  @Override
  public void cleanup(@NotNull Project project) {
    super.cleanup(project);
    mySessionProfile = null;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    final Project project = holder.getManager().getProject();
    if (myConfigurations.isEmpty()) return PsiElementVisitor.EMPTY_VISITOR;

    final List<Configuration> configurations;
    final InspectionProfileImpl profile;
    for (Configuration configuration : myConfigurations) {
      configuration.initialize();
    }
    profile = (mySessionProfile != null) ? mySessionProfile : InspectionProfileManager.getInstance(project).getCurrentProfile();
    configurations = ContainerUtil.filter(myConfigurations, x -> profile.isToolEnabled(HighlightDisplayKey.find(x.getUuid().toString())));
    if (configurations.isEmpty()) return PsiElementVisitor.EMPTY_VISITOR;

    final Map<Configuration, Matcher> compiledOptions =
      SSBasedInspectionCompiledPatternsCache.getCompiledOptions(configurations, project);
    if (compiledOptions.isEmpty()) return PsiElementVisitor.EMPTY_VISITOR;

    final PairProcessor<MatchResult, Configuration> processor = (matchResult, configuration) -> {
      final PsiElement element = matchResult.getMatch();
      if (holder.getFile() != element.getContainingFile()) return false;
      final String name = ObjectUtils.notNull(configuration.getProblemDescriptor(), configuration.getName());
      final LocalQuickFix fix = createQuickFix(project, matchResult, configuration);
      final ProblemDescriptor descriptor =
        holder.getManager().createProblemDescriptor(element, name, fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly);
      holder.registerProblem(new ProblemDescriptorWithReporterName((ProblemDescriptorBase)descriptor, configuration.getUuid().toString()));
      return true;
    };
    for (Map.Entry<Configuration, Matcher> entry : compiledOptions.entrySet()) {
      final Configuration configuration = entry.getKey();
      final Matcher matcher = entry.getValue();
      matcher.getMatchContext().setSink(new DuplicateFilteringResultSink(new InspectionResultSink(processor, configuration)));
    }

    return new PsiElementVisitor() {

      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (LexicalNodesFilter.getInstance().accepts(element)) return;
        synchronized (LOCK) {
          final SsrFilteringNodeIterator matchedNodes = new SsrFilteringNodeIterator(element);
          for (Map.Entry<Configuration, Matcher> entry : compiledOptions.entrySet()) {
            final Configuration configuration = entry.getKey();
            final Matcher matcher = entry.getValue();
            if (matcher == null) continue;

            if (matcher.checkIfShouldAttemptToMatch(matchedNodes) &&
                profile.isToolEnabled(HighlightDisplayKey.find(configuration.getUuid().toString()), element)) {
              final int nodeCount = matcher.getMatchContext().getPattern().getNodeCount();
              try {
                matcher.processMatchesInElement(new CountingNodeIterator(nodeCount, matchedNodes));
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

  @Override
  public List<LocalInspectionToolWrapper> getChildren() {
    return getConfigurations().stream()
      .filter(configuration -> configuration.getOrder() == 0)
      .map(configuration -> new StructuralSearchInspectionToolWrapper(configuration))
      .collect(Collectors.toList());
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

  public List<Configuration> getConfigurations() {
    return Collections.unmodifiableList(myConfigurations);
  }

  public List<Configuration> getConfigurationsWithUuid(@NotNull UUID uuid) {
    return ContainerUtil.filter(myConfigurations, c -> uuid.equals(c.getUuid()));
  }

  public void addConfiguration(@NotNull Configuration configuration) {
    myConfigurations.add(configuration);
  }

  public void removeConfiguration(@NotNull Configuration configuration) {
    for (int i = 0, size = myConfigurations.size(); i < size; i++) {
      final Configuration c = myConfigurations.get(i);
      if (c.equals(configuration)) {
        myConfigurations.remove(i);
        return;
      }
    }
  }

  public void removeConfigurationWithUuid(@NotNull UUID uuid) {
    myConfigurations.removeIf(c -> c.getUuid().equals(uuid));
  }

  private static class InspectionResultSink extends DefaultMatchResultSink {
    private final Configuration myConfiguration;
    private PairProcessor<? super MatchResult, ? super Configuration> myProcessor;

    InspectionResultSink(PairProcessor<? super MatchResult, ? super Configuration> processor, Configuration configuration) {
      myProcessor = processor;
      myConfiguration = configuration;
    }

    @Override
    public void newMatch(MatchResult result) {
      myProcessor.process(result, myConfiguration);
    }

    @Override
    public void matchingFinished() {
      myProcessor = null;
    }
  }
}
