// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.inspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.ProblemDescriptorWithReporterName;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.dupLocator.iterators.CountingNodeIterator;
import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextLikeFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.NaturalComparator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.ui.CustomInspectionActions;
import com.intellij.profile.codeInspection.ui.InspectionMetaDataDialog;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.impl.matcher.iterators.SsrFilteringNodeIterator;
import com.intellij.structuralsearch.impl.matcher.predicates.ScriptSupport;
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.ConfigurationManager;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING;

public class SSBasedInspection extends LocalInspectionTool implements DynamicGroupTool {
  private static final Logger LOG = Logger.getInstance(SSBasedInspection.class);

  public static final Comparator<? super Configuration> CONFIGURATION_COMPARATOR =
    Comparator.comparing(Configuration::getName, NaturalComparator.INSTANCE).thenComparingInt(Configuration::getOrder);

  private static final Key<Map<Configuration, Matcher>> COMPILED_PATTERNS = Key.create("SSR_COMPILED_PATTERNS");
  private final MultiMapEx<Configuration, Matcher> myCompiledPatterns = new MultiMapEx<>();

  public static final @NonNls String SHORT_NAME = "SSBasedInspection";
  private final List<Configuration> myConfigurations = ContainerUtil.createLockFreeCopyOnWriteList();

  private final Set<String> myProblemsReported = new HashSet<>(1);
  private InspectionProfileImpl mySessionProfile;

  public static @NotNull SSBasedInspection getStructuralSearchInspection(@NotNull InspectionProfile profile) {
    return (SSBasedInspection)CustomInspectionActions.getInspection(profile, SHORT_NAME);
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
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

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    myProblemsReported.clear();
    myConfigurations.clear();
    ConfigurationManager.readConfigurations(node, myConfigurations);
    Configuration previous = null;
    boolean sorted = true;
    for (Configuration configuration : myConfigurations) {
      if (configuration.getOrder() != 0 || previous != null && CONFIGURATION_COMPARATOR.compare(previous, configuration) > 0) {
        sorted = false;
        break;
      }
      previous = configuration;
    }
    if (sorted) {
      previous = null;
      for (Configuration configuration : myConfigurations) {
        if (previous != null && previous.getUuid().equals(configuration.getUuid())) {
          configuration.setOrder(previous.getOrder() + 1); // restore order
        }
        previous = configuration;
      }
    }
  }

  @Override
  public @NotNull String getGroupDisplayName() {
    return getGeneralGroupName();
  }

  @Override
  public @NotNull @NonNls String getShortName() {
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

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    if (myConfigurations.isEmpty()) return PsiElementVisitor.EMPTY_VISITOR;
    final PsiFile file = holder.getFile();
    final FileType fileType = file.getFileType();
    if (fileType instanceof PlainTextLikeFileType) return PsiElementVisitor.EMPTY_VISITOR;

    final Project project = holder.getProject();
    final InspectionProfileImpl profile =
      (mySessionProfile != null && !isOnTheFly) ? mySessionProfile : InspectionProfileManager.getInstance(project).getCurrentProfile();
    final List<Configuration> configurations = new SmartList<>();
    for (Configuration configuration : myConfigurations) {
      if (configuration.getFileType() != fileType && !configuration.getMatchOptions().isSearchInjectedCode()) continue;
      final ToolsImpl tools = profile.getToolsOrNull(configuration.getUuid(), project);
      if (tools != null && tools.isEnabled(file)) {
        configurations.add(configuration);
        register(configuration);
      }
    }
    if (configurations.isEmpty()) return PsiElementVisitor.EMPTY_VISITOR;

    Set<SmartPsiElementPointer<?>> duplicates = ContainerUtil.newConcurrentSet();
    final Map<Configuration, Matcher> compiledPatterns = checkOutCompiledPatterns(configurations, project, holder, duplicates);
    session.putUserData(COMPILED_PATTERNS, compiledPatterns);
    return new SSBasedVisitor(compiledPatterns);
  }

  public static void register(@NotNull Configuration configuration) {
    if (configuration.getOrder() != 0) {
      // not a main configuration containing meta data
      return;
    }
    // modify from single (event) thread, to prevent race conditions.
    ApplicationManager.getApplication().invokeLater(() -> {
      final String shortName = configuration.getUuid();
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
    }, ModalityState.nonModal());
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
    return new StructuralQuickFix(project, matchResult, configuration.getReplaceOptions());
  }

  private @NotNull Configuration getMainConfiguration(@NotNull Configuration configuration) {
    if (configuration.getOrder() == 0) {
      return configuration;
    }
    final String uuid = configuration.getUuid();
    return myConfigurations.stream().filter(c -> c.getOrder() == 0 && uuid.equals(c.getUuid())).findFirst().orElse(configuration);
  }

  public @NotNull List<Configuration> getConfigurations() {
    return new SmartList<>(myConfigurations);
  }

  public @NotNull @Unmodifiable List<Configuration> getConfigurationsWithUuid(@NotNull String uuid) {
    return ContainerUtil.sorted(ContainerUtil.filter(myConfigurations, c -> uuid.equals(c.getUuid())),
                                Comparator.comparingInt(Configuration::getOrder));
  }

  public boolean addConfiguration(@NotNull Configuration configuration) {
    if (myConfigurations.contains(configuration)) {
      return false;
    }
    myConfigurations.add(configuration);
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
    return myConfigurations.remove(configuration);
  }

  public boolean removeConfigurationsWithUuid(@NotNull String uuid) {
    return myConfigurations.removeIf(c -> c.getUuid().equals(uuid));
  }

  public InspectionMetaDataDialog createMetaDataDialog(Project project, @NotNull String profileName, @Nullable Configuration configuration) {
    final List<Configuration> configurations = getConfigurations();
    final Function<String, @Nullable @NlsContexts.DialogMessage String> nameValidator = name -> {
      for (Configuration current : configurations) {
        if (current.getOrder() == 0 && current.getName().equals(name) &&
            (configuration == null || !current.getUuid().equals(configuration.getUuid()))) {
          return SSRBundle.message("inspection.with.name.exists.warning", name);
        }
      }
      return null;
    };
    if (configuration == null) {
      return new InspectionMetaDataDialog(project, profileName, nameValidator);
    }
    return new InspectionMetaDataDialog(project, profileName, nameValidator, configuration.getName(), configuration.getDescription(),
                                        configuration.getProblemDescriptor(), configuration.getSuppressId());
  }

  private static class StructuralQuickFix implements LocalQuickFix {
    private final ReplacementInfo myReplacementInfo;
    private final Replacer myReplacer;
    private final ReplaceOptions myReplaceOptions;

    StructuralQuickFix(@NotNull Project project, @NotNull MatchResult matchResult, @NotNull ReplaceOptions replaceOptions) {
      myReplaceOptions = replaceOptions;
      myReplacer = new Replacer(project, replaceOptions);
      myReplacementInfo = myReplacer.buildReplacement(matchResult);
    }

    @Override
    public @NotNull String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", myReplacementInfo.getReplacement());
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (element != null) {
        myReplacer.replace(myReplacementInfo);
      }
    }

    @Override
    public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
      return new StructuralQuickFix(target.getProject(), new MatchResultForPreview(myReplacementInfo.getMatchResult(), target), myReplaceOptions);
    }

    @Override
    public @NotNull String getFamilyName() {
      //noinspection DialogTitleCapitalization
      return SSRBundle.message("SSRInspection.family.name");
    }
  }

  private final class InspectionResultSink extends DefaultMatchResultSink {
    private final Configuration myConfiguration;
    private ProblemsHolder myHolder;

    private final Set<? super SmartPsiElementPointer<?>> duplicates;

    InspectionResultSink(@NotNull Configuration configuration, @NotNull ProblemsHolder holder, @NotNull Set<? super SmartPsiElementPointer<?>> duplicates) {
      myConfiguration = configuration;
      myHolder = holder;
      this.duplicates = duplicates;
    }

    @Override
    public void newMatch(@NotNull MatchResult result) {
      if (!duplicates.add(result.getMatchRef())) {
        return;
      }
      registerProblem(result, myConfiguration, myHolder);
    }

    private void registerProblem(@NotNull MatchResult matchResult, @NotNull Configuration configuration, @NotNull ProblemsHolder holder) {
      final PsiElement element = matchResult.getMatch();
      PsiFile containingFile = element.getContainingFile();
      PsiFile templateFile = PsiUtilCore.getTemplateLanguageFile(containingFile);
      if (!element.isPhysical() || holder.getFile() != containingFile && holder.getFile() != templateFile) {
        return;
      }
      final LocalQuickFix fix = createQuickFix(element.getProject(), matchResult, configuration);
      final Configuration mainConfiguration = getMainConfiguration(configuration);
      final String name = ObjectUtils.notNull(mainConfiguration.getProblemDescriptor(), mainConfiguration.getName());
      final InspectionManager manager = holder.getManager();
      final ProblemDescriptor descriptor =
        manager.createProblemDescriptor(element, name, fix, GENERIC_ERROR_OR_WARNING, holder.isOnTheFly());
      final String toolName = configuration.getUuid();
      holder.registerProblem(new ProblemDescriptorWithReporterName((ProblemDescriptorBase)descriptor, toolName));
    }

    @Override
    public void matchingFinished() {
      duplicates.clear();
      myHolder = null; // to avoid leaking holder with InspectionProblemHolder retaining LocalInspectionPass
    }
  }

  private @NotNull Map<Configuration, Matcher> checkOutCompiledPatterns(@NotNull List<? extends Configuration> configurations,
                                                                        @NotNull Project project, @NotNull ProblemsHolder holder,
                                                                        @NotNull Set<? super SmartPsiElementPointer<?>> duplicates) {
    final Map<Configuration, Matcher> result = new HashMap<>();
    for (Configuration configuration : configurations) {
      Matcher matcher = myCompiledPatterns.popValue(configuration);
      if (matcher == Matcher.EMPTY) {
        continue;
      }
      if (matcher != null) {
        result.put(configuration, matcher);
      }
      else {
        matcher = buildCompiledConfiguration(configuration, project);
        result.put(configuration, matcher);
      }
      if (matcher != null) {
        MatchContext context = matcher.getMatchContext();
        context.setSink(new InspectionResultSink(configuration, holder, duplicates));
        // ssr should never match recursively because this is handled by the inspection visitor
        context.setShouldRecursivelyMatch(false);
      }
    }
    return result;
  }

  @ApiStatus.Internal @TestOnly
  public void compileAllConfigurations(@NotNull Project project) {
    for (Configuration configuration : myConfigurations) {
      buildCompiledConfiguration(configuration, project);
    }
  }

  private static Matcher buildCompiledConfiguration(@NotNull Configuration configuration, @NotNull Project project) {
    try {
      final MatchOptions matchOptions = configuration.getMatchOptions();
      final CompiledPattern compiledPattern = PatternCompiler.compilePattern(project, matchOptions, false, true);
      return (compiledPattern == null) ? null : new Matcher(project, matchOptions, compiledPattern);
    } catch (StructuralSearchException e) {
      LOG.warn("Malformed structural search inspection pattern \"" + configuration.getName() + '"', e);
      return null;
    }
  }

  private void checkInCompiledPatterns(@NotNull Map<Configuration, Matcher> compiledPatterns) {
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

    V popValue(K k) {
      final Deque<V> vs = (Deque<V>)myMap.get(k);
      return vs == null ? null : vs.pollLast();
    }
  }

  private class SSBasedVisitor extends PsiElementVisitor {

    private final Map<Configuration, Matcher> myCompiledOptions;

    SSBasedVisitor(Map<Configuration, Matcher> compiledOptions) {
      myCompiledOptions = compiledOptions;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (LexicalNodesFilter.getInstance().accepts(element)) return;
      for (Map.Entry<Configuration, Matcher> entry : myCompiledOptions.entrySet()) {
        Matcher matcher = entry.getValue();
        if (matcher == null) continue;
        MatchingStrategy strategy = matcher.getMatchContext().getPattern().getStrategy();
        if (!strategy.continueMatching(element)) continue;
        Configuration configuration = entry.getKey();
        processElement(element, configuration, matcher);
      }
    }

    private void processElement(@Nullable PsiElement element, @NotNull Configuration configuration, @NotNull Matcher matcher) {
      final NodeIterator matchedNodes = SsrFilteringNodeIterator.create(element);
      if (!matcher.checkIfShouldAttemptToMatch(matchedNodes)) {
        return;
      }
      final MatchContext matchContext = matcher.getMatchContext();
      final int nodeCount = matchContext.getPattern().getNodeCount();
      try {
        matcher.processMatchesInElement(new CountingNodeIterator(nodeCount, matchedNodes));
      }
      catch (StructuralSearchException e) {
        if (myProblemsReported.add(configuration.getName())) { // don't overwhelm the user with messages
          final String message = e.getMessage().replace(ScriptSupport.UUID, "");
          NotificationGroupManager.getInstance()
            .getNotificationGroup(UIUtil.SSR_NOTIFICATION_GROUP_ID)
            .createNotification(SSRBundle.message("inspection.script.problem", message, configuration.getName()), NotificationType.ERROR)
            .setImportant(true)
            .notify(element != null ? element.getProject() : null);
        }
      }
      finally {
        matchedNodes.reset();
      }
    }
  }
}
