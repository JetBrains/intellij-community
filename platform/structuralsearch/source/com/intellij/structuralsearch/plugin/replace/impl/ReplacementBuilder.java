// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.replace.impl;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil;
import com.intellij.structuralsearch.impl.matcher.PatternTreeContext;
import com.intellij.structuralsearch.impl.matcher.predicates.ScriptSupport;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import groovy.lang.Script;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author maxim
 */
public final class ReplacementBuilder {
  @NotNull
  private final String replacement;
  private final MultiMap<String, ParameterInfo> parameterizations = MultiMap.createLinked();
  private final Map<String, ScriptSupport> replacementVarsMap = new HashMap<>();
  private final ReplaceOptions options;
  private final Project myProject;

  ReplacementBuilder(@NotNull Project project, @NotNull ReplaceOptions options) {
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
      parameterizations.putValue(name, info);
    }

    final LanguageFileType fileType = options.getMatchOptions().getFileType();
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(fileType);
    if (profile != null) {
      try {
        final Language dialect = options.getMatchOptions().getDialect();
        assert dialect != null;
        final PatternContextInfo context = new PatternContextInfo(PatternTreeContext.Block, options.getMatchOptions().getPatternContext());
        final PsiElement[] elements =
          MatcherImplUtil.createTreeFromText(options.getReplacement(), context, fileType, dialect, project, false);
        if (elements.length > 0) {
          final PsiElement patternNode = elements[0].getParent();
          patternNode.accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
              final String text = element.getText();
              if (MatchUtil.isTypedVariable(text)) {
                final Collection<ParameterInfo> infos = findParameterization(Replacer.stripTypedVariableDecoration(text));
                for (ParameterInfo info : infos) {
                  if (info.getElement() == null) {
                    info.setElement(element);
                    return;
                  }
                }
              }
              super.visitElement(element);
            }
          });
          profile.provideAdditionalReplaceOptions(patternNode, options, this);
        }
      } catch (IncorrectOperationException e) {
        throw new MalformedPatternException(e.getMessage());
      }
    }
  }

  @NotNull
  String process(@NotNull MatchResult match, @NotNull ReplacementInfo replacementInfo, @NotNull LanguageFileType type) {
    if (parameterizations.isEmpty()) {
      return replacement;
    }

    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(type);
    assert profile != null;

    final List<ParameterInfo> sorted = new SmartList<>(parameterizations.values());
    sorted.sort(Comparator.comparingInt(ParameterInfo::getStartIndex).reversed());
    final StringBuilder result = new StringBuilder(replacement);
    for (ParameterInfo info : sorted) {
      final MatchResult r = replacementInfo.getNamedMatchResult(info.getName());
      if (info.isReplacementVariable()) {
        final Object replacement = generateReplacement(info, match);
        if (replacement == null && r != null) {
          profile.handleSubstitution(info, r, result, replacementInfo);
        }
        else {
          Replacer.insertSubstitution(result, 0, info, String.valueOf(replacement));
        }
      }
      else if (r != null) {
        profile.handleSubstitution(info, r, result, replacementInfo);
      }
      else {
        profile.handleNoSubstitution(info, result);
      }
    }

    return result.toString();
  }

  @Nullable
  private Object generateReplacement(@NotNull ParameterInfo info, @NotNull MatchResult match) {
    ScriptSupport scriptSupport = replacementVarsMap.get(info.getName());

    if (scriptSupport == null) {
      final String constraint = options.getVariableDefinition(info.getName()).getScriptCodeConstraint();
      final List<String> variableNames = ContainerUtil.map(options.getVariableDefinitions(), o -> o.getName());
      final String name = info.getName();
      final String scriptText = StringUtil.unquoteString(constraint);
      try {
        final Script script = ScriptSupport.buildScript(name, scriptText, options.getMatchOptions());
        scriptSupport = new ScriptSupport(myProject, script, name, variableNames);
        replacementVarsMap.put(info.getName(), scriptSupport);
      } catch (MalformedPatternException e) {
        return null;
      }
    }
    return scriptSupport.evaluate(match, null);
  }

  public Collection<ParameterInfo> findParameterization(String name) {
    return parameterizations.get(name);
  }

  public ParameterInfo findParameterization(PsiElement element) {
    if (element == null) return null;
    final String text = element.getText();
    if (!MatchUtil.isTypedVariable(text)) return null;
    return ContainerUtil.find(findParameterization(Replacer.stripTypedVariableDecoration(text)), info -> info.getElement() == element);
  }
}
