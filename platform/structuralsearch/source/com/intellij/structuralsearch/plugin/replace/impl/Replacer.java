/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.structuralsearch.plugin.replace.impl;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
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
  private ReplacementBuilder replacementBuilder;
  private ReplaceOptions options;
  private ReplacementContext context;
  private StructuralReplaceHandler replaceHandler;
  private PsiElement lastAffectedElement = null;

  public Replacer(Project project, ReplaceOptions options) {
    this.project = project;
    this.options = options;
  }

  public static String stripTypedVariableDecoration(final String type) {
   return type.substring(1,type.length()-1);
 }

  public static int insertSubstitution(StringBuilder result, int offset, final ParameterInfo info, String image) {
   if (image.length() > 0) result.insert(offset+ info.getStartIndex(),image);
   offset += image.length();
   return offset;
 }

  public String testReplace(String in, String what, String by, ReplaceOptions options) throws IncorrectOperationException {
    return testReplace(in, what, by, options, false);
  }

  public String testReplace(String in, String what, String by, ReplaceOptions options, boolean filePattern) {
    FileType type = options.getMatchOptions().getFileType();
    return testReplace(in, what, by, options, filePattern, false, type, null);
  }

  public String testReplace(String in, String what, String by, ReplaceOptions options, boolean filePattern, boolean createPhysicalFile,
                            FileType sourceFileType, Language sourceDialect) {
    this.options = options;
    final MatchOptions matchOptions = this.options.getMatchOptions();
    this.options.setReplacement(by);
    replacementBuilder=null;
    context = null;
    replaceHandler = null;

    matchOptions.clearVariableConstraints();
    matchOptions.fillSearchCriteria(what);

    Matcher.validate(project, matchOptions);
    checkSupportedReplacementPattern(project, options);

    Matcher matcher = new Matcher(project);
    try {
      PsiElement firstElement, lastElement, parent;

      if (options.getMatchOptions().getScope() == null) {
        PsiElement[] elements = MatcherImplUtil.createTreeFromText(
          in,
          filePattern ? PatternTreeContext.File : PatternTreeContext.Block,
          sourceFileType, sourceDialect, null,
          project,
          createPhysicalFile
        );

        firstElement = elements[0];
        lastElement = elements[elements.length-1];
        parent = firstElement.getParent();

        matchOptions.setScope(new LocalSearchScope(elements));
      } else {
        parent = ((LocalSearchScope)options.getMatchOptions().getScope()).getScope()[0];
        firstElement = parent.getFirstChild();
        lastElement = parent.getLastChild();
      }

      matchOptions.setResultIsContextMatch(true);
      CollectingMatchResultSink sink = new CollectingMatchResultSink();
      matcher.testFindMatches(sink, matchOptions);

      final List<ReplacementInfo> resultPtrList = new SmartList<>();

      for (final MatchResult result : sink.getMatches()) {
        resultPtrList.add(buildReplacement(result));
      }

      int startOffset = firstElement.getTextRange().getStartOffset();
      int endOffset = filePattern ? 0 : parent.getTextLength() - lastElement.getTextRange().getEndOffset();

      // get nodes from text may contain
      PsiElement prevSibling = firstElement.getPrevSibling();
      if (prevSibling instanceof PsiWhiteSpace) {
        startOffset -= prevSibling.getTextLength() - 1;
      }

      PsiElement nextSibling = lastElement.getNextSibling();
      if (nextSibling instanceof PsiWhiteSpace) {
        endOffset -= nextSibling.getTextLength() - 1;
      }

      replaceAll(resultPtrList);

      String result = parent.getText();
      result = result.substring(startOffset);
      result = result.substring(0,result.length() - endOffset);

      return result;
    }
    catch (Exception e) {
      throw new IncorrectOperationException(e);
    }
    finally {
      options.getMatchOptions().setScope(null);
    }
  }

  public void replaceAll(final List<ReplacementInfo> infos) {
    for (ReplacementInfo info : infos) {
      PsiElement element = info.getMatch(0);
      initContextAndHandler(element);
      if (replaceHandler != null) {
        replaceHandler.prepare(info);
      }
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
    initContextAndHandler(info.getMatch(0));

    if (replaceHandler != null) {
      replaceHandler.prepare(info);
    }
    reformatAndPostProcess(doReplace(info));
  }

  @Nullable
  private PsiElement doReplace(ReplacementInfo info) {
    final PsiElement element = info.getMatch(0);

    if (element==null || !element.isWritable() || !element.isValid()) return null;

    final PsiElement elementParent = element.getParent();

    CodeStyleManager.getInstance(project).performActionWithFormatterDisabled(
      (Runnable)() -> {
        if (replaceHandler != null) {
          replaceHandler.replace(info, options);
        }
      }
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
    if (replaceHandler != null) {
      replaceHandler.postProcess(elementParent, options);
    }
  }

  private void initContextAndHandler(PsiElement psiContext) {
    if (context == null) {
      context = new ReplacementContext(options, project);
    }
    if (replaceHandler == null) {
      final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(psiContext);
      if (profile != null) {
        replaceHandler = profile.getReplaceHandler(context);
      }
    }
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

  public static void checkSupportedReplacementPattern(Project project, ReplaceOptions options) throws UnsupportedPatternException {
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

        if (j==segmentCount2) {
          ReplacementVariableDefinition definition = options.getVariableDefinition(replacementSegmentName);

          if (definition == null || definition.getScriptCodeConstraint().length() <= 2 /*empty quotes*/) {
            throw new UnsupportedPatternException(
              SSRBundle.message("replacement.variable.is.not.defined.message", replacementSegmentName)
            );
          } else {
            String message = ScriptSupport.checkValidScript(StringUtil.stripQuotesAroundValue(definition.getScriptCodeConstraint()));
            if (message != null) {
              throw new UnsupportedPatternException(
                SSRBundle.message("replacement.variable.is.not.valid", replacementSegmentName, message)
              );
            }
          }
        }
      }

      StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(fileType);
      assert profile != null;
      profile.checkReplacementPattern(project, options);

    } catch(IncorrectOperationException ex) {
      throw new UnsupportedPatternException(SSRBundle.message("incorrect.pattern.message"));
    }
  }

  public ReplacementInfo buildReplacement(MatchResult result) {
    final ReplacementInfoImpl replacementInfo = new ReplacementInfoImpl(result, project);

    if (replacementBuilder==null) {
      replacementBuilder = new ReplacementBuilder(project, options);
    }
    replacementInfo.setReplacement(replacementBuilder.process(result, replacementInfo, options.getMatchOptions().getFileType()));

    return replacementInfo;
  }
}
