package com.intellij.grazie.utils;

import ai.grazie.nlp.langs.Language;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.codeInspection.ex.ToolsImpl;
import com.intellij.grazie.GrazieConfig;
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection;
import com.intellij.grazie.jlanguage.Lang;
import com.intellij.grazie.text.TextContent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.StringOperation;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static com.intellij.grazie.text.TextExtractor.findAllTextContents;


public final class HighlightingUtil {

  public static final Comparator<TextContent> BY_TEXT_START = Comparator.comparing(tc -> tc.textOffsetToFile(0));

  public static Set<TextContent.TextDomain> checkedDomains() {
    return GrazieInspection.Companion.checkedDomains();
  }

  public static TextRange selectionRange(Editor editor) {
    return new TextRange(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd());
  }

  public static ModificationTracker grazieConfigTracker() {
    return ApplicationManager.getApplication().getService(GrazieConfig.class);
  }

  public static boolean isTooLargeText(List<TextContent> texts) {
    return texts.stream().mapToInt(t -> t.length()).sum() > 50_000;
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

  private static final Pattern trackerIssuePrefix = Pattern.compile("\\s*([A-Z]\\w+-\\d+):?\\s+.*");

  public static int stripPrefix(TextContent content) {
    int start = 0;
    String text = content.toString();

    if (CommitMessage.isCommitMessage(content.getContainingFile())) {
      if (text.startsWith("[")) {
        int rBrace = text.indexOf(']');
        if (rBrace >= 1 && rBrace <= 30) {
          start = rBrace + 1;
        }
      } else {
        int colon = text.indexOf(':');
        if (colon >= 1 && colon <= 30 && text.substring(0, colon).chars().filter(Character::isWhitespace).count() <= 1) {
          start = colon + 1;
        }
      }

      var issueMatch = trackerIssuePrefix.matcher(text.substring(start));

      if (issueMatch.matches()) {
        start += issueMatch.group(1).length() + 1;
      }
    }

    while (start < content.length() && Character.isWhitespace(content.charAt(start))) {
      start++;
    }

    return start;
  }

  public static List<TextContent> getCheckedFileTexts(FileViewProvider vp) {
    return CachedValuesManager.getManager(vp.getManager().getProject()).getCachedValue(vp, () -> {
      List<TextContent> contents = ContainerUtil.sorted(findAllTextContents(vp, checkedDomains()), BY_TEXT_START);
      return CachedValueProvider.Result.create(contents, vp.getAllFiles().getFirst(), grazieConfigTracker());
    });
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
