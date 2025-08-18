package com.intellij.grazie.text;

import ai.grazie.nlp.langs.Language;
import ai.grazie.rules.en.EnglishParameters;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.grazie.GrazieConfig;
import com.intellij.grazie.jlanguage.Lang;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

record ChangeLanguageVariant(Lang from, Lang to, boolean wasOxford, boolean toOxford, String text) implements LocalQuickFix {

  static final String BRITISH_OXFORD_ID = EnglishParameters.Variant.BRITISH_OXFORD_ID;

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return text;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    var action = new BasicUndoableAction(descriptor.getPsiElement().getContainingFile().getVirtualFile()) {
      @Override
      public void undo() {
        changeVariant(to, from, wasOxford);
      }

      @Override
      public void redo() {
        changeVariant(from, to, toOxford);
      }
    };
    action.redo();
    UndoManager.getInstance(project).undoableActionPerformed(action);
    //todo FUS?
  }

  @SuppressWarnings("deprecation")
  private static void changeVariant(Lang from, Lang to, boolean toOxford) {
    GrazieConfig.Companion.update(s -> {
      Set<Lang> languages =  EnumSet.copyOf(s.getEnabledLanguages());
      languages.remove(from);
      languages.add(to);

      return s.copy(
        languages, s.getEnabledGrammarStrategies(), s.getDisabledGrammarStrategies(),
        s.getEnabledCommitIntegration(),
        s.getUserDisabledRules(), s.getUserEnabledRules(),
        s.getSuppressingContext(), s.getDetectionContext(), s.getCheckingContext(), s.getVersion(),
        s.getStyleProfile(), s.getParameters(), s.getUseOxfordSpelling(), s.getAutoFix()
      );
    });
    if (from.isEnglish()) {
      GrazieConfig.Companion.update(state -> state.withOxfordSpelling(toOxford));
    }
  }

  @Nullable
  static LocalQuickFix create(Language language, String toVariant, String text) {
    Lang from = StreamEx.of(GrazieConfig.Companion.get().getAvailableLanguages())
      .findFirst(l -> language.getIso().equals(l.getIso()))
      .orElse(null);
    if (from == null) return null;

    boolean toOxford = BRITISH_OXFORD_ID.equals(toVariant);
    String toCountry = toOxford ? "GB" : toVariant;
    Lang[] values = Lang.values();
    Lang to = ContainerUtil.find(values, l -> l.getIso().equals(from.getIso()) && toCountry.equals(countryCode(l)));
    return to == null ? null : new ChangeLanguageVariant(from, to, GrazieConfig.Companion.get().getUseOxfordSpelling(), toOxford, text);
  }

  private static @Nullable String countryCode(Lang l) { //todo this should be in IDEA
    return switch (l) {
      case AMERICAN_ENGLISH -> "US";
      case BRITISH_ENGLISH -> "GB";
      case CANADIAN_ENGLISH -> "CA";
      case GERMANY_GERMAN -> "DE";
      case AUSTRIAN_GERMAN -> "AT";
      //todo Swiss German in IDEA
      default -> null;
    };
  }
}
