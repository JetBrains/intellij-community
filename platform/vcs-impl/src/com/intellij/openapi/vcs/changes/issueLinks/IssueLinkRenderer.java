/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.issueLinks;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.IssueNavigationConfiguration;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Consumer;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class IssueLinkRenderer {
  private final SimpleColoredComponent myColoredComponent;
  private final IssueNavigationConfiguration myIssueNavigationConfiguration;

  public IssueLinkRenderer(final Project project, final SimpleColoredComponent coloredComponent) {
    myColoredComponent = coloredComponent;
    myIssueNavigationConfiguration = IssueNavigationConfiguration.getInstance(project);
  }

  public List<String> appendTextWithLinks(final String text) {
    return appendTextWithLinks(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  public List<String> appendTextWithLinks(final String text, final SimpleTextAttributes baseStyle) {
    return appendTextWithLinks(text, baseStyle, new Consumer<String>() {
      @Override
      public void consume(String s) {
        append(s, baseStyle);
      }
    });
  }

  public List<String> appendTextWithLinks(final String text, final SimpleTextAttributes baseStyle, final Consumer<String> consumer) {
    final List<String> pieces = new ArrayList<String>();
    final List<IssueNavigationConfiguration.LinkMatch> list = myIssueNavigationConfiguration.findIssueLinks(text);
    int pos = 0;
    final SimpleTextAttributes linkAttributes = getLinkAttributes(baseStyle);
    for(IssueNavigationConfiguration.LinkMatch match: list) {
      final TextRange textRange = match.getRange();
      if (textRange.getStartOffset() > pos) {
        final String piece = text.substring(pos, textRange.getStartOffset());
        pieces.add(piece);
        consumer.consume(piece);
      }
      final String piece = textRange.substring(text);
      pieces.add(piece);
      append(piece, linkAttributes, match);
      pos = textRange.getEndOffset();
    }
    if (pos < text.length()) {
      final String piece = text.substring(pos);
      pieces.add(piece);
      consumer.consume(piece);
    }
    return pieces;
  }

  private void append(final String piece, final SimpleTextAttributes baseStyle) {
    myColoredComponent.append(piece, baseStyle);
  }

  private void append(final String piece, final SimpleTextAttributes baseStyle, final IssueNavigationConfiguration.LinkMatch match) {
    myColoredComponent.append(piece, baseStyle, new SimpleColoredComponent.BrowserLauncherTag(match.getTargetUrl()));
  }

  private static SimpleTextAttributes getLinkAttributes(final SimpleTextAttributes baseStyle) {
    return (baseStyle.getStyle() & SimpleTextAttributes.STYLE_BOLD) != 0 ?
           SimpleTextAttributes.LINK_BOLD_ATTRIBUTES : SimpleTextAttributes.LINK_ATTRIBUTES;
  }
}
