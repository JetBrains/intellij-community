package com.intellij.grazie.utils;

import ai.grazie.nlp.langs.Language;
import ai.grazie.nlp.stripper.PrefixStripper;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.codeInspection.ex.ToolsImpl;
import com.intellij.grazie.GrazieConfig;
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection;
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection.Companion.TextContentRelatedData;
import com.intellij.grazie.jlanguage.Lang;
import com.intellij.grazie.text.TextContent;
import com.intellij.grazie.text.TextContent.TextDomain;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.StringOperation;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static com.intellij.grazie.ide.inspection.grammar.GrazieInspection.MAX_TEXT_LENGTH_IN_PSI_ELEMENT;
import static com.intellij.grazie.text.TextExtractor.findAllTextContents;


public final class HighlightingUtil {

  private final static Logger LOGGER = Logger.getInstance(HighlightingUtil.class);
  private static final Key<Object> LOCK = Key.create("grazie reliable language detection cache lock");

  public static final Comparator<TextContent> BY_TEXT_START = Comparator.comparing(tc -> tc.textOffsetToFile(0));

  public static Set<TextDomain> checkedDomains() {
    return GrazieInspection.Companion.checkedDomains();
  }

  public static TextRange selectionRange(Editor editor) {
    return new TextRange(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd());
  }

  public static ModificationTracker grazieConfigTracker() {
    return ApplicationManager.getApplication().getService(GrazieConfig.class);
  }

  public static boolean isTooLargeText(List<TextContent> texts) {
    return texts.stream().mapToInt(t -> t.length()).sum() > MAX_TEXT_LENGTH_IN_PSI_ELEMENT;
  }

  public static void applyTextChanges(Document document, List<StringOperation> changes) {
    for (StringOperation r : StreamEx.of(changes).sortedBy(c -> -c.getRange().getStartOffset())) {
      document.replaceString(r.getRange().getStartOffset(), r.getRange().getEndOffset(), r.getReplacement());
    }
  }

  public static @Nullable Lang findInstalledLang(@NotNull Language language) {
    return StreamEx.of(GrazieConfig.Companion.get().getAvailableLanguages())
      .findFirst(lang -> lang.getIso() == language.getIso())
      .orElse(null);
  }

  public static int stripPrefix(TextContent content) {
    if (CommitMessage.isCommitMessage(content.getContainingFile())) {
      return PrefixStripper.stripPrefix(content);
    }
    int start = 0;
    while (start < content.length() && isSpace(content.charAt(start))) {
      start++;
    }
    return start;
  }

  public static List<TextContent> getCheckedFileTexts(FileViewProvider vp) {
    Set<TextDomain> domains = checkedDomains();
    return ContainerUtil.filter(getAllFileTexts(vp), tc -> domains.contains(tc.getDomain()));
  }

  public static List<TextContent> getAllFileTexts(FileViewProvider vp) {
    Object lock = ConcurrencyUtil.computeIfAbsent(vp, LOCK, Object::new);
    synchronized (lock) {
      return CachedValuesManager.getManager(vp.getManager().getProject()).getCachedValue(vp, () -> {
        List<TextContent> contents = ContainerUtil.sorted(findAllTextContents(vp, TextDomain.ALL), BY_TEXT_START);
        PsiFile file = vp.getAllFiles().getFirst();
        TextContentRelatedData contentRelatedData = new TextContentRelatedData(file, contents);
        LOGGER.debug("Evaluating texts of:", contentRelatedData);
        return CachedValueProvider.Result.create(contentRelatedData, file, grazieConfigTracker());
      }).getContents();
    }
  }

  public static boolean isSpace(Character symbol) {
    return Character.isWhitespace(symbol) || Character.isSpaceChar(symbol);
  }

  public static boolean isLowercase(@NotNull CharSequence content) {
    return content.chars().allMatch(c -> !Character.isLetter(c) || Character.isLowerCase(c));
  }

  public static boolean isInspectionEnabled(String shortName, PsiFile file) {
    InspectionProfileImpl profile = getActiveProfile(file);
    ToolsImpl tools = profile.getToolsOrNull(shortName, file.getProject());
    return tools != null && tools.isEnabled(file);
  }

  public static <T extends LocalInspectionTool> @Nullable T getTool(PsiFile file, String shortName, Class<T> toolClass) {
    InspectionProfileImpl profile = getActiveProfile(file);
    ToolsImpl tools = profile.getToolsOrNull(shortName, file.getProject());
    if (tools == null || !tools.isEnabled(file)) return null;
    return toolClass.cast(tools.getInspectionTool(file).getTool());
  }

  private static InspectionProfileImpl getActiveProfile(PsiFile file) {
    Project project = file.getProject();
    InspectionProfile profile = InspectionProfileManager.getInstance(project).getCurrentProfile();
    var customizer = InspectionProfileWrapper.getCustomInspectionProfileWrapper(file);
    return (InspectionProfileImpl) (customizer != null ? customizer.apply(profile).getInspectionProfile() : profile);
  }
}
