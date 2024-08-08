// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.replace.impl;

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil;
import com.intellij.structuralsearch.impl.matcher.PatternTreeContext;
import com.intellij.structuralsearch.impl.matcher.predicates.ScriptSupport;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.structuralsearch.plugin.util.CollectingMatchResultSink;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Maxim.Mossienko
 */
public class Replacer {
  private final Project project;
  @NotNull
  private final ReplaceOptions options;
  private final StructuralReplaceHandler replaceHandler;
  private final ReplacementBuilder replacementBuilder;
  private PsiElement lastAffectedElement;

  public Replacer(@NotNull Project project, @NotNull ReplaceOptions options) {
    this.project = project;
    this.options = options;
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(options.getMatchOptions().getFileType());
    assert profile != null;
    replaceHandler = profile.getReplaceHandler(project, options);
    assert replaceHandler != null;
    replacementBuilder = new ReplacementBuilder(this.project, this.options);
  }

  public static int insertSubstitution(StringBuilder result, int offset, final ParameterInfo info, String image) {
    if (!image.isEmpty()) {
      result.insert(offset + info.getStartIndex(), image);
      offset += image.length();
    }
    return offset;
  }

  public static String testReplace(String in, String what, String by, ReplaceOptions options, Project project)  {
    return testReplace(in, what, by, options, project, false);
  }

  public static String testReplace(String in, String what, String by, ReplaceOptions options, Project project, boolean sourceIsFile) {
    final LanguageFileType fileType = options.getMatchOptions().getFileType();
    assert fileType != null;
    return testReplace(in, what, by, options, project, sourceIsFile, false, fileType, fileType.getLanguage());
  }

  public static String testReplace(String in, String what, String by, ReplaceOptions replaceOptions, Project project, boolean sourceIsFile,
                                   boolean createPhysicalFile, @NotNull LanguageFileType sourceFileType, @NotNull Language sourceDialect) {
    replaceOptions.setReplacement(by);

    final MatchOptions matchOptions = replaceOptions.getMatchOptions();
    matchOptions.fillSearchCriteria(what);

    Matcher.validate(project, matchOptions);
    checkReplacementPattern(project, replaceOptions);

    final Replacer replacer = new Replacer(project, replaceOptions);
    final Matcher matcher = new Matcher(project, matchOptions);
    try {
      final PsiElement firstElement;
      final PsiElement lastElement;
      final PsiElement parent;
      if (matchOptions.getScope() == null) {
        final PsiElement[] elements = MatcherImplUtil.createTreeFromText(
          in,
          new PatternContextInfo(sourceIsFile ? PatternTreeContext.File : PatternTreeContext.Block),
          sourceFileType,
          sourceDialect,
          project,
          createPhysicalFile
        );

        firstElement = elements[0];
        lastElement = elements[elements.length-1];
        parent = firstElement.getParent();

        matchOptions.setScope(new LocalSearchScope(elements));
      } else {
        parent = ((LocalSearchScope)matchOptions.getScope()).getScope()[0];
        firstElement = parent.getFirstChild();
        lastElement = parent.getLastChild();
      }

      final CollectingMatchResultSink sink = new CollectingMatchResultSink();
      matcher.testFindMatches(sink);

      final List<ReplacementInfo> replacements = new SmartList<>();
      for (final MatchResult result : sink.getMatches()) {
        replacements.add(replacer.buildReplacement(result));
      }

      int startOffset = firstElement.getTextRange().getStartOffset();
      int endOffset = sourceIsFile ? 0 : (parent.getTextLength() - lastElement.getTextRange().getEndOffset());

      // get nodes from text may contain
      final PsiElement prevSibling = firstElement.getPrevSibling();
      if (prevSibling instanceof PsiWhiteSpace) {
        startOffset -= prevSibling.getTextLength();
      }

      final PsiElement nextSibling = lastElement.getNextSibling();
      if (nextSibling instanceof PsiWhiteSpace) {
        endOffset -= nextSibling.getTextLength();
      }
      replacer.replaceAll(replacements);
      if (firstElement == lastElement && firstElement instanceof PsiFile) {
        return firstElement.getText();
      }
      final String result = parent.getText();
      return result.substring(startOffset, result.length() - endOffset);
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (Exception e) {
      throw new IncorrectOperationException(e);
    }
    finally {
      matchOptions.setScope(null);
    }
  }

  public void replaceAll(final List<? extends ReplacementInfo> infos) {
    for (ReplacementInfo info : infos) {
      replaceHandler.prepare(info);
    }
    if (IntentionPreviewUtils.isIntentionPreviewActive()) {
      doReplaceAll(infos, new EmptyProgressIndicator());
    } else {
      ((ApplicationEx)ApplicationManager.getApplication()).runWriteActionWithCancellableProgressInDispatchThread(
        SSRBundle.message("structural.replace.title"),
        project,
        null,
        indicator -> doReplaceAll(infos, indicator)
      );
    }
  }

  private void doReplaceAll(@NotNull List<? extends ReplacementInfo> infos, @NotNull ProgressIndicator indicator) {
     indicator.setIndeterminate(false);
    try {
      final int size = infos.size();
      VirtualFile lastFile = null;
      for (int i = 0; i < size; i++) {
        indicator.checkCanceled();
        indicator.setFraction((float)(i + 1) / size);

        final ReplacementInfo info = infos.get(i);
        final PsiElement element = info.getMatch(0);
        if (element == null) {
          continue;
        }
        final VirtualFile vFile = element.getContainingFile().getVirtualFile();
        if (vFile != null && !vFile.equals(lastFile)) {
          indicator.setText2(vFile.getPresentableUrl());
          lastFile = vFile;
        }

        ProgressManager.getInstance().executeNonCancelableSection(() -> {
          final PsiElement affectedElement = doReplace(info);
          if (affectedElement != lastAffectedElement) {
            if (lastAffectedElement != null) reformatAndPostProcess(lastAffectedElement);
            lastAffectedElement = affectedElement;
          }
        });
      }
    } finally {
      ProgressManager.getInstance().executeNonCancelableSection(() -> reformatAndPostProcess(lastAffectedElement));
    }
  }

  public void replace(@NotNull ReplacementInfo info) {
    replaceHandler.prepare(info);
    reformatAndPostProcess(doReplace(info));
  }

  @Nullable
  private PsiElement doReplace(@NotNull ReplacementInfo info) {
    final PsiElement element = info.getMatch(0);

    if (element==null || !element.isWritable() || !element.isValid()) return null;

    final PsiElement elementParent = StructuralSearchUtil.getPresentableElement(element).getParent();

    CodeStyleManager.getInstance(project).performActionWithFormatterDisabled(
      (Runnable)() -> replaceHandler.replace(info, options)
    );

    if (!elementParent.isValid() || !elementParent.isWritable()) {
      return null;
    }

    return elementParent;
  }

  private void reformatAndPostProcess(final PsiElement elementParent) {
    if (elementParent == null || !elementParent.isValid()) return;
    final PsiFile containingFile = elementParent.getContainingFile();

    replaceHandler.postProcess(elementParent, options);
    if (containingFile != null && options.isToReformatAccordingToStyle()) {
      final VirtualFile file = containingFile.getVirtualFile();
      if (file != null) {
        final Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document != null) {
          PsiDocumentManager.getInstance(project).commitDocument(document);
        }
      }

      final int parentOffset = elementParent.getTextRange().getStartOffset();
      CodeStyleManager.getInstance(project).reformatRange(containingFile, parentOffset, parentOffset + elementParent.getTextLength(), true);
    }
  }

  public static void handleComments(final PsiElement el, final PsiElement replacement, ReplacementInfo replacementInfo) {
    final PsiElement lastChild = el.getLastChild();
    if (lastChild instanceof PsiComment &&
        replacementInfo.getVariableName(lastChild) == null &&
        !(replacement.getLastChild() instanceof PsiComment)) {
      PsiElement firstElementAfterStatementEnd = lastChild;
      for(PsiElement curElement=firstElementAfterStatementEnd.getPrevSibling();curElement!=null;curElement = curElement.getPrevSibling()) {
        if (!(curElement instanceof PsiWhiteSpace) && !(curElement instanceof PsiComment)) break;
        firstElementAfterStatementEnd = curElement;
      }
      replacement.addRangeAfter(firstElementAfterStatementEnd,lastChild,replacement.getLastChild());
    }

    final PsiElement firstChild = el.getFirstChild();
    if (firstChild instanceof PsiComment &&
        !(firstChild instanceof PsiDocCommentBase) &&
        replacementInfo.getVariableName(firstChild) == null) {
      PsiElement lastElementBeforeStatementStart = firstChild;

      for(PsiElement curElement=lastElementBeforeStatementStart.getNextSibling();curElement!=null;curElement = curElement.getNextSibling()) {
        if (!(curElement instanceof PsiWhiteSpace) && !(curElement instanceof PsiComment)) break;
        lastElementBeforeStatementStart = curElement;
      }
      replacement.addRangeBefore(firstChild,lastElementBeforeStatementStart,replacement.getFirstChild());
    }
  }

  public static void checkReplacementPattern(@NotNull Project project, @NotNull ReplaceOptions options) {
    try {
      final String search = options.getMatchOptions().getSearchPattern();
      final String replacement = options.getReplacement();
      final Template searchTemplate = TemplateManager.getInstance(project).createTemplate("" , "", search);
      final Template replaceTemplate = TemplateManager.getInstance(project).createTemplate("", "", replacement);

      final int segmentCount = replaceTemplate.getSegmentsCount();
      for(int i = 0; i < segmentCount; i++) {
        final String replacementSegmentName = replaceTemplate.getSegmentName(i);
        final int segmentCount2  = searchTemplate.getSegmentsCount();
        int j = 0;

        while (j < segmentCount2) {
          final String searchSegmentName = searchTemplate.getSegmentName(j);
          if (replacementSegmentName.equals(searchSegmentName)) break;

          // Reference to
          if (replacementSegmentName.startsWith(searchSegmentName) && replacementSegmentName.charAt(searchSegmentName.length()) == '_') {
            try {
              Integer.parseInt(replacementSegmentName.substring(searchSegmentName.length() + 1));
              break;
            } catch (NumberFormatException ignore) {}
          }
          j++;
        }

        if (j == segmentCount2) {
          final ReplacementVariableDefinition definition = options.getVariableDefinition(replacementSegmentName);

          if (definition == null || definition.getScriptCodeConstraint().length() <= 2 /*empty quotes*/) {
            throw new MalformedPatternException(SSRBundle.message("replacement.variable.is.not.defined.message", replacementSegmentName));
          } else {
            final String scriptText = StringUtil.unquoteString(definition.getScriptCodeConstraint());
            try {
              ScriptSupport.buildScript(definition.getName(), scriptText, options.getMatchOptions());
            } catch (MalformedPatternException e) {
              throw new MalformedPatternException(
                SSRBundle.message("replacement.variable.is.not.valid", replacementSegmentName, e.getLocalizedMessage())
              );
            }
          }
        }
      }

      final LanguageFileType fileType = options.getMatchOptions().getFileType();
      final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(fileType);
      if (profile != null) {
        ReadAction.run(() -> profile.checkReplacementPattern(project, options));
      }
    } catch (IncorrectOperationException ex) {
      throw new MalformedPatternException(SSRBundle.message("incorrect.pattern.message"));
    }
  }

  @NotNull
  public ReplacementInfo buildReplacement(@NotNull MatchResult result) {
    final ReplacementInfoImpl replacementInfo = new ReplacementInfoImpl(result, project);
    final LanguageFileType fileType = options.getMatchOptions().getFileType();
    assert fileType != null;
    replacementInfo.setReplacement(replacementBuilder.process(result, replacementInfo, fileType));

    return replacementInfo;
  }
}
