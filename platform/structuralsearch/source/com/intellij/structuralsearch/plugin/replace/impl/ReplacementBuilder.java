// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import java.util.stream.Collectors;

/**
 * @author maxim
 */
public final class ReplacementBuilder {
  private final String replacement;
  private final List<ParameterInfo> parameterizations = new SmartList<>();
  private final Map<String, ScriptSupport> replacementVarsMap = new HashMap<>();
  private final ReplaceOptions options;
  private final Project myProject;

  ReplacementBuilder(Project project, ReplaceOptions options) {
    myProject = project;
    this.options = options;

    final Template template = TemplateManager.getInstance(project).createTemplate("", "", options.getReplacement());
    replacement = template.getTemplateText();

    int prevOffset = 0;
    for (int i = 0; i < template.getSegmentsCount(); i++) {
      final int offset = template.getSegmentOffset(i);
      final String name = template.getSegmentName(i);
      final ParameterInfo info = new ParameterInfo(name, offset, options.getVariableDefinition(name) != null);

      // find delimiter
      int pos = offset - 1;
      while (pos >= prevOffset && pos < replacement.length() && StringUtil.isWhiteSpace(replacement.charAt(pos))) {
        pos--;
      }

      if (pos >= 0) {
        if (replacement.charAt(pos) == ',') {
          info.setHasCommaBefore(true);
        }
        while (pos > prevOffset && StringUtil.isWhiteSpace(replacement.charAt(pos - 1))) {
          pos--;
        }
        info.setBeforeDelimiterPos(pos);
      }

      pos = offset;
      while (pos < replacement.length() && StringUtil.isWhiteSpace(replacement.charAt(pos))) {
        pos++;
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
      prevOffset = offset;
      parameterizations.add(info);
    }

    FileType fileType = options.getMatchOptions().getFileType();
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(fileType);
    if (profile != null) {
      try {
        final PsiElement[] elements = MatcherImplUtil.createTreeFromText(
          options.getReplacement(),
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
      final String constraint = options.getVariableDefinition(info.getName()).getScriptCodeConstraint();
      final List<String> variableNames =
        options.getVariableDefinitions().stream().map(o -> o.getName()).collect(Collectors.toList());
      scriptSupport = new ScriptSupport(myProject, StringUtil.unquoteString(constraint), info.getName(), variableNames);
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
