/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class IssueLinkRenderer {
  private final SimpleColoredComponent myColoredComponent;
  private final IssueNavigationConfiguration myIssueNavigationConfiguration;

  public IssueLinkRenderer(final Project project, final SimpleColoredComponent coloredComponent) {
    myIssueNavigationConfiguration = IssueNavigationConfiguration.getInstance(project);
    myColoredComponent = coloredComponent;
  }

  public List<String> appendTextWithLinks(String text) {
    return appendTextWithLinks(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  public List<String> appendTextWithLinks(String text, SimpleTextAttributes baseStyle) {
    final List<String> pieces = new ArrayList<String>();
    final List<IssueNavigationConfiguration.LinkMatch> list = myIssueNavigationConfiguration.findIssueLinks(text);
    int pos = 0;
    for(IssueNavigationConfiguration.LinkMatch match: list) {
      final TextRange textRange = match.getRange();
      if (textRange.getStartOffset() > pos) {
        final String piece = text.substring(pos, textRange.getStartOffset());
        pieces.add(piece);
        myColoredComponent.append(piece, baseStyle);
      }
      final String piece = textRange.substring(text);
      pieces.add(piece);
      myColoredComponent.append(piece, getLinkAttributes(baseStyle),
                                new TreeLinkMouseListener.BrowserLauncher(match.getTargetUrl()));
      pos = textRange.getEndOffset();
    }
    if (pos < text.length()) {
      final String piece = text.substring(pos);
      pieces.add(piece);
      myColoredComponent.append(piece, baseStyle);
    }
    return pieces;
  }

  private static SimpleTextAttributes getLinkAttributes(final SimpleTextAttributes baseStyle) {
    return (baseStyle.getStyle() & SimpleTextAttributes.STYLE_BOLD) != 0 ? SimpleTextAttributes.LINK_BOLD_ATTRIBUTES : SimpleTextAttributes.LINK_ATTRIBUTES;
  }
}
