// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.replace.impl;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Maxim.Mossienko
 */
public class Replacer {
  private final Project project;
  private final ReplaceOptions options;
  private final StructuralReplaceHandler replaceHandler;
  private final ReplacementBuilder replacementBuilder;
  private PsiElement lastAffectedElement = null;

  public Replacer(Project project, ReplaceOptions options) {
    this.project = project;
    this.options = options;
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(options.getMatchOptions().getFileType());
    assert profile != null;
    replaceHandler = profile.getReplaceHandler(new ReplacementContext(options, project));
    assert replaceHandler != null;
    replacementBuilder = new ReplacementBuilder(this.project, this.options);
  }

  public static String stripTypedVariableDecoration(final String type) {
    return type.substring(1, type.length() - 1);
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
    FileType type = options.getMatchOptions().getFileType();
    return testReplace(in, what, by, options, project, sourceIsFile, false, type, null);
  }

  public static String testReplace(String in, String what, String by, ReplaceOptions replaceOptions, Project project, boolean sourceIsFile,
                                   boolean createPhysicalFile, FileType sourceFileType, Language sourceDialect) {
    replaceOptions.setReplacement(by);

    final MatchOptions matchOptions = replaceOptions.getMatchOptions();
    matchOptions.fillSearchCriteria(what);

    Matcher.validate(project, matchOptions);
    checkSupportedReplacementPattern(project, replaceOptions);

    final Replacer replacer = new Replacer(project, replaceOptions);
    final Matcher matcher = new Matcher(project);
    try {
      final PsiElement firstElement, lastElement, parent;

      if (replaceOptions.getMatchOptions().getScope() == null) {
        final PsiElement[] elements = MatcherImplUtil.createTreeFromText(
          in,
          sourceIsFile ? PatternTreeContext.File : PatternTreeContext.Block,
          sourceFileType, sourceDialect, null,
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

      CollectingMatchResultSink sink = new CollectingMatchResultSink();
      matcher.testFindMatches(sink, matchOptions);

      final List<ReplacementInfo> resultPtrList = new SmartList<>();
      for (final MatchResult result : sink.getMatches()) {
        resultPtrList.add(replacer.buildReplacement(result));
      }

      int startOffset = firstElement.getTextRange().getStartOffset();
      int endOffset = sourceIsFile ? 0 : parent.getTextLength() - lastElement.getTextRange().getEndOffset();

      // get nodes from text may contain
      final PsiElement prevSibling = firstElement.getPrevSibling();
      if (prevSibling instanceof PsiWhiteSpace) {
        startOffset -= prevSibling.getTextLength() - 1;
      }

      final PsiElement nextSibling = lastElement.getNextSibling();
      if (nextSibling instanceof PsiWhiteSpace) {
        endOffset -= nextSibling.getTextLength() - 1;
      }

      replacer.replaceAll(resultPtrList);

      String result = parent.getText();
      result = result.substring(startOffset);
      result = result.substring(0,result.length() - endOffset);

      return result;
    }
    catch (Exception e) {
      throw new IncorrectOperationException(e);
    }
    finally {
      matchOptions.setScope(null);
    }
  }

  public void replaceAll(final List<ReplacementInfo> infos) {
    for (ReplacementInfo info : infos) {
      replaceHandler.prepare(info);
    }

    ((ApplicationImpl)ApplicationManager.getApplication()).runWriteActionWithCancellableProgressInDispatchThread(
      SSRBundle.message("structural.replace.title"),
      project,
      null,
      indicator -> {
        indicator.setIndeterminate(false);
        try {
          final int size = infos.size();
          VirtualFile lastFile = null;
          for (int i = 0; i < size; i++) {
            indicator.checkCanceled();
            indicator.setFraction((float)(i + 1) / size);

            ReplacementInfo info = infos.get(i);
            PsiElement element = info.getMatch(0);
            assert element != null;
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
    );
  }

  public void replace(ReplacementInfo info) {
    replaceHandler.prepare(info);
    reformatAndPostProcess(doReplace(info));
  }

  @Nullable
  private PsiElement doReplace(ReplacementInfo info) {
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
    if (elementParent == null) return;
    final PsiFile containingFile = elementParent.getContainingFile();

    if (containingFile != null && options.isToReformatAccordingToStyle()) {
      final VirtualFile file = containingFile.getVirtualFile();
      if (file != null) {
        PsiDocumentManager.getInstance(project).commitDocument(FileDocumentManager.getInstance().getDocument(file));
      }

      final int parentOffset = elementParent.getTextRange().getStartOffset();
      CodeStyleManager.getInstance(project).reformatRange(containingFile, parentOffset, parentOffset + elementParent.getTextLength(), true);
    }
    replaceHandler.postProcess(elementParent, options);
  }

  public static void handleComments(final PsiElement el, final PsiElement replacement, ReplacementInfo replacementInfo) {
    final PsiElement lastChild = el.getLastChild();
    if (lastChild instanceof PsiComment &&
        replacementInfo.getVariableName(lastChild) == null &&
        !(replacement.getLastChild() instanceof PsiComment)
      ) {
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
        replacementInfo.getVariableName(firstChild) == null
        ) {
      PsiElement lastElementBeforeStatementStart = firstChild;

      for(PsiElement curElement=lastElementBeforeStatementStart.getNextSibling();curElement!=null;curElement = curElement.getNextSibling()) {
        if (!(curElement instanceof PsiWhiteSpace) && !(curElement instanceof PsiComment)) break;
        lastElementBeforeStatementStart = curElement;
      }
      replacement.addRangeBefore(firstChild,lastElementBeforeStatementStart,replacement.getFirstChild());
    }
  }

  public static void checkSupportedReplacementPattern(Project project, ReplaceOptions options) {
    try {
      String search = options.getMatchOptions().getSearchPattern();
      String replacement = options.getReplacement();
      FileType fileType = options.getMatchOptions().getFileType();
      Template template = TemplateManager.getInstance(project).createTemplate("","",search);
      Template template2 = TemplateManager.getInstance(project).createTemplate("","",replacement);

      int segmentCount = template2.getSegmentsCount();
      for(int i=0;i<segmentCount;++i) {
        final String replacementSegmentName = template2.getSegmentName(i);
        final int segmentCount2  = template.getSegmentsCount();
        int j;

        for(j=0;j<segmentCount2;++j) {
          final String searchSegmentName = template.getSegmentName(j);

          if (replacementSegmentName.equals(searchSegmentName)) break;

          // Reference to
          if (replacementSegmentName.startsWith(searchSegmentName) &&
              replacementSegmentName.charAt(searchSegmentName.length())=='_'
             ) {
            try {
              Integer.parseInt(replacementSegmentName.substring(searchSegmentName.length()+1));
              break;
            } catch(NumberFormatException ex) {}
          }
        }

        if (j == segmentCount2) {
          ReplacementVariableDefinition definition = options.getVariableDefinition(replacementSegmentName);

          if (definition == null || definition.getScriptCodeConstraint().length() <= 2 /*empty quotes*/) {
            throw new MalformedPatternException(SSRBundle.message("replacement.variable.is.not.defined.message", replacementSegmentName));
          } else {
            String message = ScriptSupport.checkValidScript(StringUtil.unquoteString(definition.getScriptCodeConstraint()));
            if (message != null) {
              throw new MalformedPatternException(SSRBundle.message("replacement.variable.is.not.valid", replacementSegmentName, message));
            }
          }
        }
      }

      StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(fileType);
      assert profile != null;
      ReadAction.run(() -> profile.checkReplacementPattern(project, options));
    } catch (IncorrectOperationException ex) {
      throw new MalformedPatternException(SSRBundle.message("incorrect.pattern.message"));
    }
  }

  public ReplacementInfo buildReplacement(MatchResult result) {
    final ReplacementInfoImpl replacementInfo = new ReplacementInfoImpl(result, project);
    replacementInfo.setReplacement(replacementBuilder.process(result, replacementInfo, options.getMatchOptions().getFileType()));

    return replacementInfo;
  }
}
