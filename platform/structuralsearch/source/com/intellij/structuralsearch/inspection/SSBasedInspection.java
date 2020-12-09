// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.inspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.ProblemDescriptorWithReporterName;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.dupLocator.iterators.CountingNodeIterator;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileTypes.PlainTextLikeFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.NaturalComparator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.structuralsearch.*;
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
import com.intellij.structuralsearch.plugin.util.SmartPsiPointer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING;

public class SSBasedInspection extends LocalInspectionTool implements DynamicGroupTool {
  public static final Comparator<? super Configuration> CONFIGURATION_COMPARATOR =
    Comparator.comparing(Configuration::getName, NaturalComparator.INSTANCE).thenComparingInt(Configuration::getOrder);
  private static final Object LOCK = ObjectUtils.sentinel("SSRLock"); // hack to avoid race conditions in SSR

  private static final Key<Map<Configuration, Matcher>> COMPILED_PATTERNS = Key.create("SSR_COMPILED_PATTERNS");
  private final MultiMapEx<Configuration, Matcher> myCompiledPatterns = new MultiMapEx<>();

  @NonNls public static final String SHORT_NAME = "SSBasedInspection";
  private final List<Configuration> myConfigurations = ContainerUtil.createLockFreeCopyOnWriteList();

  private boolean myWriteSorted = false;
  private final Set<String> myProblemsReported = new HashSet<>(1);
  private InspectionProfileImpl mySessionProfile;

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (myWriteSorted) {
      // need copy, because lock-free copy-on-write list doesn't support sorting
      final ArrayList<Configuration> configurations = new ArrayList<>(myConfigurations);

      // ordered like in UI by name and pattern order
      // (for easier textual diffing between inspection profiles, because the order doesn't change as long as the name doesn't change)
      Collections.sort(configurations, CONFIGURATION_COMPARATOR);
      ConfigurationManager.writeConfigurations(node, configurations);

      // no order attribute written
      for (Element child : node.getChildren()) {
        child.removeAttribute("order");
      }
    }
    else {
      ConfigurationManager.writeConfigurations(node, myConfigurations);
    }
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    myProblemsReported.clear();
    myConfigurations.clear();
    ConfigurationManager.readConfigurations(node, myConfigurations);
    Configuration previous = null;
    boolean sorted = true;
    for (Configuration configuration : myConfigurations) {
      if (previous != null) {
        if (CONFIGURATION_COMPARATOR.compare(previous, configuration) >= 0 || configuration.getOrder() != 0) {
          sorted = false;
          break;
        }
        if (previous.getUuid().equals(configuration.getUuid())) {
          configuration.setOrder(previous.getOrder() + 1);
        }
      }
      previous = configuration;
    }
    if (sorted) myWriteSorted = sorted; // write sorted if already sorted
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
    myCompiledPatterns.clear();
  }

  @Override
  public void inspectionFinished(@NotNull LocalInspectionToolSession session, @NotNull ProblemsHolder problemsHolder) {
    final Map<Configuration, Matcher> compiledPatterns = session.getUserData(COMPILED_PATTERNS);
    if (compiledPatterns != null) {
      checkInCompiledPatterns(compiledPatterns);
    }
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    if (myConfigurations.isEmpty()) return PsiElementVisitor.EMPTY_VISITOR;
    final PsiFile file = holder.getFile();
    if (file.getFileType() instanceof PlainTextLikeFileType) return PsiElementVisitor.EMPTY_VISITOR;

    final Project project = holder.getProject();
    final InspectionProfileImpl profile =
      (mySessionProfile != null && !isOnTheFly) ? mySessionProfile : InspectionProfileManager.getInstance(project).getCurrentProfile();
    final List<Configuration> configurations = new SmartList<>();
    for (Configuration configuration : myConfigurations) {
      final ToolsImpl tools = profile.getToolsOrNull(configuration.getUuid().toString(), project);
      if (tools != null && tools.isEnabled()) {
        configurations.add(configuration);
        register(configuration);
      }
    }
    if (configurations.isEmpty()) return PsiElementVisitor.EMPTY_VISITOR;

    final Map<Configuration, Matcher> compiledPatterns;
    if (!Registry.is("ssr.multithreaded.inspection")) {
      compiledPatterns = SSBasedInspectionCompiledPatternsCache.getInstance(project).getCachedCompiledConfigurations(configurations);
      if (compiledPatterns.isEmpty()) return PsiElementVisitor.EMPTY_VISITOR;
      synchronized (LOCK) {
        for (Map.Entry<Configuration, Matcher> entry : compiledPatterns.entrySet()) {
          final Matcher matcher = entry.getValue();
          if (matcher == null) {
            continue;
          }
          matcher.getMatchContext().setSink(new InspectionResultSink());
        }
      }
    }
    else {
      compiledPatterns = checkOutCompiledPatterns(configurations, project);
      session.putUserData(COMPILED_PATTERNS, compiledPatterns);
    }
    return new SSBasedVisitor(compiledPatterns, profile, holder);
  }

  public static void register(@NotNull Configuration configuration) {
    if (configuration.getOrder() != 0) {
      // not a main configuration containing meta data
      return;
    }
    // modify from single (AWT) thread, to prevent race conditions.
    ApplicationManager.getApplication().invokeLater(() -> {
      final String shortName = configuration.getUuid().toString();
      final HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
      if (key != null) {
        if (!isMetaDataChanged(configuration, key)) return;
        HighlightDisplayKey.unregister(shortName);
      }
      final String suppressId = configuration.getSuppressId();
      final String name = configuration.getName();
      if (suppressId == null) {
        HighlightDisplayKey.register(shortName, () -> name, SHORT_NAME);
      }
      else {
        HighlightDisplayKey.register(shortName, () -> name, suppressId, SHORT_NAME);
      }
    }, ModalityState.NON_MODAL);
  }

  private static boolean isMetaDataChanged(@NotNull Configuration configuration, @NotNull HighlightDisplayKey key) {
    if (StringUtil.isEmpty(configuration.getSuppressId())) {
      if (!SHORT_NAME.equals(key.getID())) return true;
    }
    else if (!configuration.getSuppressId().equals(key.getID())) return true;
    return !configuration.getName().equals(HighlightDisplayKey.getDisplayNameByKey(key));
  }

  @Override
  public @NotNull List<LocalInspectionToolWrapper> getChildren() {
    return getConfigurations().stream()
      .filter(configuration -> configuration.getOrder() == 0)
      .map(configuration -> new StructuralSearchInspectionToolWrapper(getConfigurationsWithUuid(configuration.getUuid())))
      .collect(Collectors.toList());
  }

  private static LocalQuickFix createQuickFix(@NotNull Project project, @NotNull MatchResult matchResult, @NotNull Configuration configuration) {
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

  @NotNull
  private Configuration getMainConfiguration(@NotNull Configuration configuration) {
    if (configuration.getOrder() == 0) {
      return configuration;
    }
    final UUID uuid = configuration.getUuid();
    return myConfigurations.stream().filter(c -> c.getOrder() == 0 && uuid.equals(c.getUuid())).findFirst().orElse(configuration);
  }

  @NotNull
  public List<Configuration> getConfigurations() {
    return new SmartList<>(myConfigurations);
  }

  @NotNull
  public List<Configuration> getConfigurationsWithUuid(@NotNull UUID uuid) {
    final List<Configuration> configurations = ContainerUtil.filter(myConfigurations, c -> uuid.equals(c.getUuid()));
    configurations.sort(Comparator.comparingInt(Configuration::getOrder));
    return configurations;
  }

  public boolean addConfiguration(@NotNull Configuration configuration) {
    if (myConfigurations.contains(configuration)) {
      return false;
    }
    myConfigurations.add(configuration);
    myWriteSorted = true;
    return true;
  }

  public boolean addConfigurations(@NotNull Collection<? extends Configuration> configurations) {
    boolean modified = false;
    for (Configuration configuration : configurations) {
      modified |= addConfiguration(configuration);
    }
    return modified;
  }

  public boolean removeConfiguration(@NotNull Configuration configuration) {
    final boolean removed = myConfigurations.remove(configuration);
    if (removed) myWriteSorted = true;
    return removed;
  }

  public boolean removeConfigurationsWithUuid(@NotNull UUID uuid) {
    final boolean removed = myConfigurations.removeIf(c -> c.getUuid().equals(uuid));
    if (removed) myWriteSorted = true;
    return removed;
  }

  private class InspectionResultSink extends DefaultMatchResultSink {
    private Configuration myConfiguration;
    private ProblemsHolder myHolder;

    private final Set<SmartPsiPointer> duplicates = new THashSet<>();

    InspectionResultSink() {}

    public void setConfigurationAndHolder(@NotNull Configuration configuration, @NotNull ProblemsHolder holder) {
      myConfiguration = configuration;
      myHolder = holder;
    }

    @Override
    public void newMatch(@NotNull MatchResult result) {
      if (!duplicates.add(result.getMatchRef())) {
        return;
      }
      registerProblem(result, myConfiguration, myHolder);
    }

    private void registerProblem(MatchResult matchResult, Configuration configuration, ProblemsHolder holder) {
      final PsiElement element = matchResult.getMatch();
      if (!element.isPhysical() || holder.getFile() != element.getContainingFile()) {
        return;
      }
      final LocalQuickFix fix = createQuickFix(element.getProject(), matchResult, configuration);
      final Configuration mainConfiguration = getMainConfiguration(configuration);
      final String name = ObjectUtils.notNull(mainConfiguration.getProblemDescriptor(), mainConfiguration.getName());
      final InspectionManager manager = holder.getManager();
      final ProblemDescriptor descriptor =
        manager.createProblemDescriptor(element, name, fix, GENERIC_ERROR_OR_WARNING, holder.isOnTheFly());
      final String toolName = configuration.getUuid().toString();
      holder.registerProblem(new ProblemDescriptorWithReporterName((ProblemDescriptorBase)descriptor, toolName));
    }

    @Override
    public void matchingFinished() {
      duplicates.clear();
    }
  }

  @Nullable
  Map<Configuration, Matcher> checkOutCompiledPatterns(@NotNull List<? extends Configuration> configurations, @NotNull Project project) {
    final Map<Configuration, Matcher> result = new HashMap<>();
    for (Configuration configuration : configurations) {
      final Matcher matcher = myCompiledPatterns.popValue(configuration);
      if (matcher == Matcher.EMPTY) {
        continue;
      }
      if (matcher != null) {
        result.put(configuration, matcher);
      }
      else {
        final Matcher newMatcher = SSBasedInspectionCompiledPatternsCache.getInstance(project).buildCompiledConfiguration(configuration);
        if (newMatcher != null) {
          newMatcher.getMatchContext().setSink(new InspectionResultSink());
        }
        result.put(configuration, newMatcher);
      }
    }
    return result;
  }

  void checkInCompiledPatterns(@NotNull Map<Configuration, Matcher> compiledPatterns) {
    for (Map.Entry<Configuration, Matcher> entry : compiledPatterns.entrySet()) {
      final Configuration configuration = entry.getKey();
      final Matcher matcher = entry.getValue();
      if (matcher == null) {
        myCompiledPatterns.putValue(configuration, Matcher.EMPTY);
      }
      else {
        matcher.getMatchContext().getSink().matchingFinished();
        myCompiledPatterns.putValue(configuration, matcher);
      }
    }
  }

  private static class MultiMapEx<K, V> extends MultiMap<K, V> {
    MultiMapEx() {
      super(new ConcurrentHashMap<>());
    }

    @Override
    protected @NotNull Collection<V> createCollection() {
      return new ConcurrentLinkedDeque<>();
    }

    public V popValue(K k) {
      final Deque<V> vs = (Deque<V>)myMap.get(k);
      return vs == null ? null : vs.pollLast();
    }
  }

  private class SSBasedVisitor extends PsiElementVisitor {

    private final Map<Configuration, Matcher> myCompiledOptions;
    private final InspectionProfileImpl myProfile;
    private @NotNull final ProblemsHolder myHolder;

    SSBasedVisitor(Map<Configuration, Matcher> compiledOptions, InspectionProfileImpl profile, @NotNull ProblemsHolder holder) {
      myCompiledOptions = compiledOptions;
      myProfile = profile;
      myHolder = holder;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (LexicalNodesFilter.getInstance().accepts(element)) return;
      if (Registry.is("ssr.multithreaded.inspection")) {
        processElement(element);
      }
      else {
        synchronized (LOCK) {
          processElement(element);
        }
      }
    }

    private void processElement(@NotNull PsiElement element) {
      for (Map.Entry<Configuration, Matcher> entry : myCompiledOptions.entrySet()) {
        final Configuration configuration = entry.getKey();
        final Matcher matcher = entry.getValue();
        if (matcher == null) continue;

        processElement(element, configuration, matcher);
      }
    }

    private void processElement(PsiElement element, Configuration configuration, Matcher matcher) {
      if (!myProfile.isToolEnabled(HighlightDisplayKey.find(configuration.getUuid().toString()), element)) {
        return;
      }
      final SsrFilteringNodeIterator matchedNodes = new SsrFilteringNodeIterator(element);
      if (!matcher.checkIfShouldAttemptToMatch(matchedNodes)) {
        return;
      }
      try {
        final MatchContext matchContext = matcher.getMatchContext();
        final InspectionResultSink sink = (InspectionResultSink)matchContext.getSink();
        sink.setConfigurationAndHolder(configuration, myHolder);
        final int nodeCount = matchContext.getPattern().getNodeCount();
        try {
          matcher.processMatchesInElement(new CountingNodeIterator(nodeCount, matchedNodes));
        }
        catch (StructuralSearchException e) {
          if (myProblemsReported.add(configuration.getName())) { // don't overwhelm the user with messages
            final String message = e.getMessage().replace(ScriptSupport.UUID, "");
            final NotificationGroup notificationGroup =
              NotificationGroupManager.getInstance().getNotificationGroup(UIUtil.SSR_NOTIFICATION_GROUP_ID);
            notificationGroup.createNotification(NotificationType.ERROR)
              .setContent(SSRBundle.message("inspection.script.problem", message, configuration.getName()))
              .setImportant(true)
              .notify(element.getProject());
          }
        }
      } finally {
        matchedNodes.reset();
      }
    }
  }
}
