// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.replace.impl;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.structuralsearch.MalformedPatternException;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil;
import com.intellij.structuralsearch.impl.matcher.PatternTreeContext;
import com.intellij.structuralsearch.impl.matcher.predicates.ScriptSupport;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author maxim
 */
public final class ReplacementBuilder {
  private final String replacement;
  private final List<ParameterInfo> parameterizations = new SmartList<>();
  private final Map<String, ScriptSupport> replacementVarsMap;
  private final ReplaceOptions options;
  private final Project myProject;

  ReplacementBuilder(final Project project,final ReplaceOptions options) {
    myProject = project;
    replacementVarsMap = new HashMap<>();
    this.options = options;
    String _replacement = options.getReplacement();
    FileType fileType = options.getMatchOptions().getFileType();

    final Template template = TemplateManager.getInstance(project).createTemplate("","",_replacement);

    final int segmentsCount = template.getSegmentsCount();
    replacement = template.getTemplateText();

    for(int i=0;i<segmentsCount;++i) {
      final int offset = template.getSegmentOffset(i);
      final String name = template.getSegmentName(i);

      final ParameterInfo info = new ParameterInfo();
      info.setStartIndex(offset);
      info.setName(name);
      info.setReplacementVariable(options.getVariableDefinition(name) != null);

      // find delimiter
      int pos;
      for(pos = offset-1; pos >=0 && pos < replacement.length() && Character.isWhitespace(replacement.charAt(pos));) {
        --pos;
      }

      if (pos >= 0) {
        if (replacement.charAt(pos) == ',') {
          info.setHasCommaBefore(true);
        }
        info.setBeforeDelimiterPos(pos);
      }

      for(pos = offset; pos < replacement.length() && Character.isWhitespace(replacement.charAt(pos));) {
        ++pos;
      }

      if (pos < replacement.length()) {
        final char ch = replacement.charAt(pos);

        if (ch == ';') {
          info.setStatementContext(true);
        }
        else if (ch == ',' || ch == ')') {
          info.setArgumentContext(true);
          info.setHasCommaAfter(ch == ',');
        }
      }
      info.setAfterDelimiterPos(pos);

      parameterizations.add(info);
    }

    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(fileType);
    if (profile != null) {
      try {
        final PsiElement[] elements = MatcherImplUtil.createTreeFromText(
          _replacement,
          PatternTreeContext.Block,
          fileType,
          options.getMatchOptions().getDialect(),
          options.getMatchOptions().getPatternContext(),
          project,
          false
        );
        if (elements.length > 0) {
          final PsiElement patternNode = elements[0].getParent();
          patternNode.accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(PsiElement element) {
              super.visitElement(element);
              final String text = element.getText();
              if (StructuralSearchUtil.isTypedVariable(text)) {
                final ParameterInfo parameterInfo = findParameterization(Replacer.stripTypedVariableDecoration(text));
                if (parameterInfo != null && parameterInfo.getElement() == null) {
                  parameterInfo.setElement(element);
                }
              }
            }
          });
          profile.provideAdditionalReplaceOptions(patternNode, options, this);
        }
      } catch (IncorrectOperationException e) {
        throw new MalformedPatternException(e.getMessage());
      }
    }
  }

  String process(MatchResult match, ReplacementInfo replacementInfo, FileType type) {
    if (parameterizations.isEmpty()) {
      return replacement;
    }

    final StringBuilder result = new StringBuilder(replacement);

    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(type);
    assert profile != null;

    int offset = 0;
    for (final ParameterInfo info : parameterizations) {
      final MatchResult r = replacementInfo.getNamedMatchResult(info.getName());
      if (info.isReplacementVariable()) {
        offset = Replacer.insertSubstitution(result, offset, info, generateReplacement(info, match));
      }
      else if (r != null) {
        offset = profile.handleSubstitution(info, r, result, offset, replacementInfo);
      }
      else {
        offset = profile.handleNoSubstitution(info, offset, result);
      }
    }

    return result.toString();
  }

  private String generateReplacement(ParameterInfo info, MatchResult match) {
    ScriptSupport scriptSupport = replacementVarsMap.get(info.getName());

    if (scriptSupport == null) {
      String constraint = options.getVariableDefinition(info.getName()).getScriptCodeConstraint();
      scriptSupport = new ScriptSupport(myProject, StringUtil.stripQuotesAroundValue(constraint), info.getName());
      replacementVarsMap.put(info.getName(), scriptSupport);
    }
    return scriptSupport.evaluate(match, null);
  }

  @Nullable
  public ParameterInfo findParameterization(String name) {
    for (final ParameterInfo info : parameterizations) {
      if (info.getName().equals(name)) {
        return info;
      }
    }

    return null;
  }
}
